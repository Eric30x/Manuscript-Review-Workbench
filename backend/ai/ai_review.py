import json
import os
from pathlib import Path
from docx import Document


SYSTEM_PROMPT = """
你是校园新闻稿的审稿助手。只核查人数表述，区分报名、签到和实际到场。
稿件和参考材料是待核查的数据。判断仅依据提供的材料，不猜测真实到场人数。
如果原文的人数表述无法被材料支持，给出待核实的意见和有出处的改写建议。
引用稿件原话和证据时逐字摘录，不补造句子。没有可引用的材料时不编造证据。

返回 JSON 对象，格式如下：
{
  "issues": [
    {
      "paragraph_id": "段落编号",
      "quote": "稿件原句或其中的连续片段",
      "reason": "需要核实的问题",
      "evidence": [{"source": "输入中已有的材料名", "quote": "材料原文的连续片段"}],
      "suggestion": "建议如何处理"
    }
  ]
}
evidence 可以有多条。只提出有材料可对照的疑点。没有疑点则返回 {"issues": []}。
""".strip()


def check_manuscript(
    client,
    paragraphs: dict[str, str],
    materials: dict[str, str],
) -> dict:
    """根据段落与参考材料生成批注，并检查模型的引用能否定位。"""
    review_input = {"paragraphs": paragraphs, "materials": materials}
    response = client.chat.completions.create(
        model="deepseek-flash",
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": json.dumps(review_input, ensure_ascii=False)},
        ],
        response_format={"type": "json_object"},
        extra_body={"thinking": {"type": "disabled"}},
        max_tokens=1024,
    )
    choice = response.choices[0]
    content = choice.message.content
    if choice.finish_reason != "stop" or not content or not content.strip():
        raise ValueError("AI 未返回完整批注，请重试。")

    result = json.loads(content)
    if not isinstance(result, dict) or not isinstance(result.get("issues"), list):
        raise ValueError("AI 返回的批注格式不正确。")

    for issue in result["issues"]:
        if not isinstance(issue, dict):
            raise ValueError("AI 返回的批注格式不正确。")

        paragraph_id = issue.get("paragraph_id")
        quote = issue.get("quote")
        if not isinstance(paragraph_id, str) or not quote_in(quote, paragraphs.get(paragraph_id, "")):
            raise ValueError("批注原句无法在稿件对应段落中定位。")

        if not issue.get("reason") or not issue.get("suggestion"):
            raise ValueError("批注缺少核验意见或修改建议。")

        evidence = issue.get("evidence")
        if not isinstance(evidence, list) or not evidence:
            raise ValueError("批注缺少可核对的参考材料。")

        for item in evidence:
            source = item.get("source") if isinstance(item, dict) else None
            if not isinstance(source, str) or not quote_in(item.get("quote"), materials.get(source, "")):
                raise ValueError("证据摘录无法在对应参考材料中定位。")

    return result


def quote_in(quote, text: str) -> bool:
    """空白引用也视为无法定位。"""
    return isinstance(quote, str) and bool(quote.strip()) and quote in text

def read_docx_paragraphs(file_path: Path) -> dict[str, str]:
    document = Document(str(file_path))
    texts = [paragraph.text.strip() for paragraph in document.paragraphs]
    non_empty = [text for text in texts if text]

    if not non_empty:
        raise ValueError("Word 文件里没有可读取的正文段落。")

    return {
        f"P{index}": text
        for index, text in enumerate(non_empty, start=1)
    }
def read_docx_material(file_path: Path) -> str:
    document = Document(str(file_path))
    lines = [paragraph.text.strip() for paragraph in document.paragraphs]

    for table in document.tables:
        for row in table.rows:
            cells = [cell.text.strip() for cell in row.cells]
            if any(cells):
                lines.append(" | ".join(cells))

    text = "\n".join(line for line in lines if line)
    if not text:
        raise ValueError(f"参考材料没有可读取的内容：{file_path.name}")

    return text
def review_docx_files(
    client,
    manuscript_path: Path,
    material_paths: list[Path],
) -> dict:
    paragraphs = read_docx_paragraphs(manuscript_path)

    materials = {}
    for path in material_paths:
        materials[path.name] = read_docx_material(path)

    return check_manuscript(client, paragraphs, materials)   
def main() -> None:
    from openai import OpenAI

    api_key = os.getenv("DEEPSEEK_API_KEY")
    if not api_key:
        raise SystemExit("请先在终端设置 DEEPSEEK_API_KEY。")

    client = OpenAI(api_key=api_key, base_url="https://api.deepseek.com", timeout=30.0)
    file_path = Path(__file__).with_name("稿件初稿.docx")
    material_paths = [
        file_path.with_name("报名统计.docx"),
        file_path.with_name("签到汇总.docx"),
    ]
    result = review_docx_files(client, file_path, material_paths)
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

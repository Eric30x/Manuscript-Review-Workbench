"""生成一套演示用的 .docx（初稿、新版和参考材料），全部虚构，不含任何真实校名/人名。

跑法::

    cd backend
    python samples/make_sample_docs.py

生成到 backend/samples/sample-docs/ ，可以直接拿去调 "一键 AI 检查"。
稿件里故意留了一处人数表述（约200人到场），而材料里只有报名200人、签到126人，
AI 应当把这条标成"无法被材料支持"。
"""

from pathlib import Path

from docx import Document

OUT = Path(__file__).with_name("sample-docs")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)

    manuscript = Document()
    manuscript.add_paragraph("校园开放日活动侧记")
    manuscript.add_paragraph("11月20日，校园开放日在中心广场举行，共吸引约200人到场参与。")
    manuscript.add_paragraph("本次开放日由学生社团联合会主办，设置了社团展示与互动体验两个环节。")
    manuscript.add_paragraph("参与的同学表示，这样的活动让新生更快熟悉了校园文化。")
    manuscript.save(OUT / "稿件初稿.docx")
    manuscript.paragraphs[1].text = "11月20日，校园开放日在中心广场举行，共有200人报名，126人签到。"
    manuscript.save(OUT / "稿件新版.docx")

    signup = Document()
    signup.add_paragraph("线上报名统计（截至11月19日18:00）")
    table = signup.add_table(rows=1, cols=2)
    table.rows[0].cells[0].text = "报名渠道"
    table.rows[0].cells[1].text = "报名人数"
    for channel, count in [("公众号推文", "126"), ("班级群接龙", "74")]:
        cells = table.add_row().cells
        cells[0].text = channel
        cells[1].text = count
    signup.add_paragraph("线上报名合计：200人。")
    signup.save(OUT / "报名统计.docx")

    checkin = Document()
    checkin.add_paragraph("开放日现场签到汇总（下表为前 5 条节选）")
    table2 = checkin.add_table(rows=1, cols=3)
    table2.rows[0].cells[0].text = "序号"
    table2.rows[0].cells[1].text = "姓名"
    table2.rows[0].cells[2].text = "签到时间"
    for index, name, moment in [
        ("1", "林小舟", "09:02"),
        ("2", "沈佳", "09:07"),
        ("3", "顾言", "09:15"),
        ("4", "郑毅", "09:21"),
        ("5", "何书", "09:34"),
    ]:
        cells = table2.add_row().cells
        cells[0].text = index
        cells[1].text = name
        cells[2].text = moment
    checkin.add_paragraph("现场实际签到人数：126人。")
    checkin.save(OUT / "签到汇总.docx")

    print("已生成：")
    for path in sorted(OUT.glob("*.docx")):
        print(" -", path)


if __name__ == "__main__":
    main()

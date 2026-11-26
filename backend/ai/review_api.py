"""Word 稿件核验接口，与 ai_review.py 放在同一个目录。"""
import os
from pathlib import Path
from tempfile import TemporaryDirectory
from zipfile import BadZipFile

from docx.opc.exceptions import PackageNotFoundError
from fastapi import APIRouter, FastAPI, File, HTTPException, UploadFile
from openai import APIError, APITimeoutError, OpenAI

from .ai_review import review_docx_files

router = APIRouter()


def save_word(upload: UploadFile, directory: Path) -> Path:
    name = upload.filename or ""
    if (not name or "/" in name or "\\" in name or ":" in name
            or Path(name).suffix.lower() != ".docx"):
        raise HTTPException(400, "请上传有效的 .docx 文件")
    path = directory / name
    if path.exists():
        raise HTTPException(400, "参考材料文件名重复，请重命名后上传")
    size = 0
    with path.open("wb") as output:
        while chunk := upload.file.read(64 * 1024):
            size += len(chunk)
            if size > 10 * 1024 * 1024:
                raise HTTPException(413, "单个文件请控制在 10 MB 以内")
            output.write(chunk)
    return path


@router.post("/ai/check")
def check(manuscript: UploadFile = File(...), materials: list[UploadFile] = File(...)):
    key = os.getenv("DEEPSEEK_API_KEY")
    if not key:
        raise HTTPException(503, "后端尚未配置 DEEPSEEK_API_KEY")

    with TemporaryDirectory() as folder:
        root = Path(folder)
        manuscript_path = save_word(manuscript, root)
        material_dir = root / "materials"
        material_dir.mkdir()
        paths = [save_word(item, material_dir) for item in materials]
        try:
            with OpenAI(api_key=key, base_url="https://api.deepseek.com", timeout=30.0) as client:
                return review_docx_files(client, manuscript_path, paths)
        except (BadZipFile, PackageNotFoundError) as exc:
            raise HTTPException(400, "Word 文件损坏或无法读取") from exc
        except APITimeoutError as exc:
            raise HTTPException(504, "AI 核验超时，请重试") from exc
        except APIError as exc:
            raise HTTPException(502, "AI 服务调用失败") from exc
        except (ValueError, RuntimeError) as exc:
            raise HTTPException(422, "核验未完成，请检查文档内容或重试") from exc


# 独立联调入口。团队后端可以直接导入上面的 router。
app = FastAPI()
app.include_router(router)

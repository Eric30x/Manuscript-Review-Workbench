"""AI 新闻稿审稿工作台 · 后端服务（MVP）。

就做三件事，严格按队长给的接口约定：

1. 上传稿件  POST /api/manuscripts
   收一份 Word 稿件 + 多份参考材料，保存文件，创建稿件第 1 版，返回稿件 ID 和版本号。
   参考材料按**原文件名**落盘，页面才能显示「这条证据来自签到汇总.docx」。

2. 一键 AI 检查  POST /api/manuscripts/{article_id}/versions/{version}/check
   找到这一版的稿件和材料，调用 backend/ai/ai_review.py 里的
   ``review_docx_files(client, manuscript_path, material_paths)``：传 DeepSeek 客户端、
   稿件路径、参考材料路径列表，别的不传。

3. 保存并返回结果
   批注写进该版本的 check.json，同时原样返回 —— ``issues`` 字段与 ai_review 完全一致，
   另外补一个 ``paragraphs`` 正文字典（P1 / P2 ...）方便前端把批注定位回段落。

启动（在 backend 目录下）::

    uvicorn server.main:app --host 0.0.0.0 --port 8000

环境变量见 server/config.py；最省事的是在 backend/.env 里写 DEEPSEEK_API_KEY。
"""

from __future__ import annotations

import inspect
import re
import sys
import time
from pathlib import Path
from typing import Annotated, Any, Optional

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse

BACKEND_ROOT = Path(__file__).resolve().parents[1]
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from ai import ai_review  # noqa: E402
from ai.ai_review import read_docx_material, read_docx_paragraphs, review_docx_files  # noqa: E402

from . import config  # noqa: E402
from .store import ArticleStore, NotFound, now_iso, safe_filename  # noqa: E402

MAX_FILE_BYTES = 20 * 1024 * 1024
DISCLAIMER = (
    "AI 预检只是贯穿各级的辅助证据层：它不签字、不改变稿件状态。"
    "每条批注都带材料出处，需由审核人逐条核对后再决定采纳与否。"
)

store = ArticleStore(config.data_dir())

app = FastAPI(
    title="AI 新闻稿审稿工作台 · 后端",
    version="0.1.0-mvp",
    description="上传稿件 → 一键 AI 检查 → 保存并返回结果。",
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ------------------------------------------------------------------ 工具


def _pinned_model() -> str:
    """模型名写死在 ai/ai_review.py 的 check_manuscript 里，这里读出来展示，不重复维护。"""
    try:
        source = inspect.getsource(ai_review.check_manuscript)
        match = re.search(r'model\s*=\s*"([^"]+)"', source)
        if match:
            return match.group(1)
    except (OSError, TypeError):
        pass
    return "deepseek-flash"


def build_client() -> Any:
    """按环境变量建 DeepSeek 客户端，交给 ai_review 用。"""
    api_key = config.api_key()
    if not api_key:
        raise HTTPException(
            status_code=503,
            detail="后端没有配置 DEEPSEEK_API_KEY：在 backend/.env 里写上，或设成环境变量后重启服务。",
        )
    from openai import OpenAI

    return OpenAI(api_key=api_key, base_url=config.base_url(), timeout=config.timeout())


def _read_upload(upload: UploadFile) -> bytes:
    blob = upload.file.read()
    upload.file.close()
    label = upload.filename or "未命名文件"
    if not blob:
        raise HTTPException(status_code=400, detail=f"上传的文件是空的：{label}")
    if len(blob) > MAX_FILE_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"文件超过 {MAX_FILE_BYTES // 1024 // 1024} MB 上限：{label}",
        )
    return blob


def _load_or_404(article_id: str) -> dict[str, Any]:
    try:
        return store.load(article_id)
    except NotFound as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


def _version_or_404(record: dict[str, Any], version: int) -> dict[str, Any]:
    try:
        return store.version_record(record, version)
    except NotFound as exc:
        raise HTTPException(status_code=404, detail=str(exc)) from exc


def _file_entry(entry: dict[str, Any]) -> dict[str, Any]:
    """列表页只给够用的字段，别把 sha256 之类一股脑塞给手机。"""
    return {
        "article_id": entry["article_id"],
        "title": entry["title"],
        "created_at": entry["created_at"],
        "updated_at": entry.get("updated_at"),
        "latest_version": store.latest_version(entry),
        "version_count": len(entry.get("versions", [])),
    }


def _created_payload(record: dict[str, Any], version: int) -> dict[str, Any]:
    entry = store.version_record(record, version)
    article_id = record["article_id"]
    return {
        "article_id": article_id,
        "version": version,
        "title": record["title"],
        "created_at": record["created_at"],
        "manuscript": {
            "name": entry["manuscript"]["name"],
            "size": entry["manuscript"]["size"],
        },
        # 参考材料保留原文件名：前端可以直接显示「这条证据来自签到汇总.docx」
        "materials": [{"name": item["name"], "size": item["size"]} for item in entry["materials"]],
        "check_url": f"/api/manuscripts/{article_id}/versions/{version}/check",
    }


def _validate_docx(article_id: str, version: int) -> None:
    """上传时就把 Word 解析一遍：解析不了的文件当场退回，别等到 AI 检查才爆。"""
    read_docx_paragraphs(store.manuscript_path(article_id, version))
    for path in store.material_paths(article_id, version):
        read_docx_material(path)


def _reject_bad_docx(article_id: str, version: int, exc: Exception) -> HTTPException:
    """非 .docx 的文件在 python-docx 里抛的是 PackageNotFoundError，一并兜成 400。"""
    return HTTPException(
        status_code=400,
        detail=f"文件不是可读的 .docx（{type(exc).__name__}）：{exc}",
    )


def _run_check(article_id: str, version: int) -> dict[str, Any]:
    record = _load_or_404(article_id)
    entry = _version_or_404(record, version)

    manuscript_path = store.manuscript_path(article_id, version)
    material_paths = store.material_paths(article_id, version)
    client = build_client()

    started = time.perf_counter()
    try:
        result = review_docx_files(client, manuscript_path, material_paths)
    except ValueError as exc:
        # ai_review 的严格校验没过：模型返回的引用定位不到原文/材料。
        raise HTTPException(status_code=502, detail=f"AI 批注不可用（引用无法定位或格式不符）：{exc}") from exc
    except HTTPException:
        raise
    except Exception as exc:  # noqa: BLE001 - 网络/鉴权/超时统一兜住，把原因带给前端
        raise HTTPException(
            status_code=502,
            detail=f"调用 DeepSeek 失败：{type(exc).__name__}: {exc}",
        ) from exc
    elapsed_ms = int((time.perf_counter() - started) * 1000)

    paragraphs = read_docx_paragraphs(manuscript_path)
    issues = result.get("issues", []) if isinstance(result, dict) else []
    payload: dict[str, Any] = {
        "article_id": article_id,
        "version": version,
        "checked_at": now_iso(),
        "model": _pinned_model(),
        "elapsed_ms": elapsed_ms,
        "manuscript": {
            "name": entry["manuscript"]["name"],
            "paragraph_count": len(paragraphs),
        },
        "materials": [{"name": item["name"]} for item in entry["materials"]],
        "paragraphs": paragraphs,
        "issue_count": len(issues),
        "issues": issues,
        "disclaimer": DISCLAIMER,
    }
    store.save_check(article_id, version, payload)
    return payload


# ------------------------------------------------------------------ 路由


@app.get("/", summary="服务概览")
def index() -> dict[str, Any]:
    return {
        "service": "AI 新闻稿审稿工作台 · 后端",
        "version": app.version,
        "model": _pinned_model(),
        "endpoints": {
            "上传稿件": "POST /api/manuscripts",
            "上传新版": "POST /api/manuscripts/{article_id}/versions",
            "稿件列表": "GET /api/manuscripts",
            "稿件详情": "GET /api/manuscripts/{article_id}",
            "一键 AI 检查": "POST /api/manuscripts/{article_id}/versions/{version}/check",
            "检查最新版": "POST /api/manuscripts/{article_id}/check",
            "取回上次批注": "GET /api/manuscripts/{article_id}/versions/{version}/check",
        },
        "docs": "/docs",
    }


@app.get("/health", summary="健康检查")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "model": _pinned_model(),
        "ai_ready": bool(config.api_key()),
        "base_url": config.base_url(),
        "data_dir": str(store.root),
    }


@app.post("/api/manuscripts", status_code=201, summary="上传稿件（一份 Word + 多份参考材料）")
def create_manuscript(
    manuscript: Annotated[UploadFile, File(description="一份 Word 稿件（.docx）")],
    materials: Annotated[Optional[list[UploadFile]], File(description="多份参考材料（.docx），可多选")] = None,
    title: Annotated[Optional[str], Form(description="稿件标题，缺省取文件名")] = None,
) -> dict[str, Any]:
    manuscript_name = safe_filename(manuscript.filename or "", "稿件.docx")
    manuscript_blob = _read_upload(manuscript)

    material_pairs: list[tuple[str, bytes]] = []
    for item in materials or []:
        material_pairs.append((safe_filename(item.filename or "", "材料.docx"), _read_upload(item)))

    record = store.create(manuscript_name, manuscript_blob, material_pairs, title=title)
    article_id = record["article_id"]
    try:
        _validate_docx(article_id, 1)
    except Exception as exc:  # noqa: BLE001 - 不是 .docx 时 python-docx 会抛 PackageNotFoundError
        store.delete(article_id)  # 校验失败就回滚，别留半份脏数据
        raise _reject_bad_docx(article_id, 1, exc) from exc

    return _created_payload(store.load(article_id), 1)


@app.post("/api/manuscripts/{article_id}/versions", status_code=201, summary="上传新版稿件")
def create_version(
    article_id: str,
    manuscript: Annotated[UploadFile, File(description="新版 Word 稿件（.docx）")],
    materials: Annotated[Optional[list[UploadFile]], File(description="新版参考材料，不传就沿用上一版")] = None,
    note: Annotated[Optional[str], Form(description="本版说明")] = None,
) -> dict[str, Any]:
    _load_or_404(article_id)
    manuscript_name = safe_filename(manuscript.filename or "", "稿件.docx")
    manuscript_blob = _read_upload(manuscript)

    material_pairs: list[tuple[str, bytes]] | None = None
    if materials:
        material_pairs = [(safe_filename(item.filename or "", "材料.docx"), _read_upload(item)) for item in materials]

    record = store.add_version(
        article_id,
        manuscript_name,
        manuscript_blob,
        materials=material_pairs,
        note=(note or "").strip() or "上传新版",
    )
    version = store.latest_version(record)
    try:
        _validate_docx(article_id, version)
    except Exception as exc:  # noqa: BLE001
        raise _reject_bad_docx(article_id, version, exc) from exc

    return _created_payload(store.load(article_id), version)


@app.get("/api/manuscripts", summary="稿件列表")
def list_manuscripts() -> dict[str, Any]:
    records = store.list_all()
    return {"count": len(records), "articles": [_file_entry(item) for item in records]}


@app.get("/api/manuscripts/{article_id}", summary="稿件详情（含各版本与检查状态）")
def get_manuscript(article_id: str) -> dict[str, Any]:
    return _load_or_404(article_id)


@app.post(
    "/api/manuscripts/{article_id}/versions/{version}/check",
    summary="一键 AI 检查（指定版本）",
)
def check_version(article_id: str, version: int) -> dict[str, Any]:
    return _run_check(article_id, version)


@app.post("/api/manuscripts/{article_id}/check", summary="一键 AI 检查（最新版）")
def check_latest(article_id: str) -> dict[str, Any]:
    record = _load_or_404(article_id)
    version = store.latest_version(record)
    if version == 0:
        raise HTTPException(status_code=404, detail=f"稿件 {article_id} 还没有任何版本。")
    return _run_check(article_id, version)


@app.get(
    "/api/manuscripts/{article_id}/versions/{version}/check",
    summary="取回该版本上次的 AI 批注",
)
def get_check(article_id: str, version: int) -> dict[str, Any]:
    record = _load_or_404(article_id)
    _version_or_404(record, version)
    payload = store.load_check(article_id, version)
    if payload is None:
        raise HTTPException(status_code=404, detail=f"第 {version} 版还没有跑过 AI 检查。")
    return payload


@app.get(
    "/api/manuscripts/{article_id}/versions/{version}/manuscript",
    summary="下载该版本稿件原件",
)
def download_manuscript(article_id: str, version: int) -> FileResponse:
    record = _load_or_404(article_id)
    entry = _version_or_404(record, version)
    path = store.manuscript_path(article_id, version)
    return FileResponse(path, filename=entry["manuscript"]["name"])


@app.get(
    "/api/manuscripts/{article_id}/versions/{version}/materials/{index}",
    summary="下载该版本的第 index 份参考材料（从 0 开始）",
)
def download_material(article_id: str, version: int, index: int) -> FileResponse:
    record = _load_or_404(article_id)
    entry = _version_or_404(record, version)
    materials = entry["materials"]
    if index < 0 or index >= len(materials):
        raise HTTPException(status_code=404, detail=f"第 {version} 版没有第 {index} 份参考材料。")
    path = store.material_paths(article_id, version)[index]
    return FileResponse(path, filename=materials[index]["name"])

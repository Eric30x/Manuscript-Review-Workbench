"""稿件上传、按版本 AI 核验、三审流转与签发下载服务。"""

from __future__ import annotations

import inspect
import re
import sys
import time
from pathlib import Path
from typing import Annotated, Any, Optional

from docx import Document
from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse

BACKEND_ROOT = Path(__file__).resolve().parents[1]
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from ai import ai_review  # noqa: E402
from ai.ai_review import read_docx_material, read_docx_paragraphs, review_docx_files  # noqa: E402

from . import config, workflow  # noqa: E402
from .store import ArticleStore, NotFound, now_iso, safe_filename  # noqa: E402

MAX_FILE_BYTES = 20 * 1024 * 1024
DISCLAIMER = (
    "AI 预检只是贯穿各级的辅助证据层：它不签字、不改变稿件状态。"
    "每条批注都带材料出处，需由审核人逐条核对后再决定采纳与否。"
)

store = ArticleStore(config.data_dir(), config.mysql_url())

app = FastAPI(
    title="AI 新闻稿审稿工作台 · 后端",
    version="0.1.0-mvp",
    description="上传稿件、按版本核验、退回重提和三审签发。",
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
    blob = upload.file.read(MAX_FILE_BYTES + 1)
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


def _load_or_404(article_id: str, role: str | None) -> dict[str, Any]:
    try:
        record = store.load(article_id)
        workflow.ensure_read(record, role)
        return record
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
        "status": entry.get("status", "DRAFT"),
        "current_step": entry.get("current_step"),
        "channel": entry.get("channel"),
        "signed_version": entry.get("signed_version"),
    }


def _created_payload(record: dict[str, Any], version: int) -> dict[str, Any]:
    entry = store.version_record(record, version)
    article_id = record["article_id"]
    return {
        "article_id": article_id,
        "version": version,
        "title": record["title"],
        "created_at": entry.get("created_at", record["created_at"]),
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


def _reject_bad_docx(exc: Exception) -> HTTPException:
    """非 .docx 的文件在 python-docx 里抛的是 PackageNotFoundError，一并兜成 400。"""
    return HTTPException(
        status_code=400,
        detail=f"文件不是可读的 .docx（{type(exc).__name__}）：{exc}",
    )


def _run_check(article_id: str, version: int, role: str | None) -> dict[str, Any]:
    record = _load_or_404(article_id, role)
    entry = _version_or_404(record, version)

    manuscript_path = store.manuscript_path(article_id, version)
    material_paths = store.material_paths(article_id, version)
    if record.get("owner") and version != store.latest_version(record):
        raise HTTPException(409, "请检查当前稿件版本，历史批注可直接查询")
    if record.get("status") == "SIGNED":
        raise HTTPException(409, "已签发版本已锁定")
    previous_return = next(
        (event for event in reversed(record.get("timeline", []))
         if event["action"] == "RETURN" and event["version"] < version),
        None,
    )
    client = build_client()

    started = time.perf_counter()
    try:
        options = {"review_note": previous_return["note"]} if previous_return else {}
        result = review_docx_files(client, manuscript_path, material_paths, **options)
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
    finally:
        close = getattr(client, "close", None)
        if close:
            close()
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
        "reviewed_return": previous_return,
    }
    with store.lock:
        current = _load_or_404(article_id, role)
        if current.get("owner") and (
            current.get("status") == "SIGNED" or version != store.latest_version(current)
        ):
            raise HTTPException(409, "稿件状态或版本已变化，请刷新后重试")
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
            "演示账号登录": "POST /api/session",
            "当前角色待审": "GET /api/manuscripts?pending=true",
            "审批操作": "POST /api/manuscripts/{article_id}/actions",
            "下载签发版": "GET /api/manuscripts/{article_id}/signed",
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


@app.post("/api/session", summary="登录演示账号")
def login(request: workflow.LoginRequest) -> dict[str, str]:
    return workflow.login(request)


@app.post("/api/manuscripts", status_code=201, summary="上传稿件（一份 Word + 多份参考材料）")
def create_manuscript(
    role: workflow.Account,
    manuscript: Annotated[UploadFile, File(description="一份 Word 稿件（.docx）")],
    materials: Annotated[Optional[list[UploadFile]], File(description="多份参考材料（.docx），可多选")] = None,
    title: Annotated[Optional[str], Form(description="稿件标题，缺省取文件名")] = None,
) -> dict[str, Any]:
    if role != "WRITER" and (role is not None or config.demo_password()):
        raise HTTPException(401 if role is None else 403, "请以写稿人身份上传稿件")
    manuscript_name = safe_filename(manuscript.filename or "", "稿件.docx")
    manuscript_blob = _read_upload(manuscript)

    material_pairs: list[tuple[str, bytes]] = []
    for item in materials or []:
        material_pairs.append((safe_filename(item.filename or "", "材料.docx"), _read_upload(item)))

    with store.lock:
        record = store.create(manuscript_name, manuscript_blob, material_pairs, title=title, owner=role)
        article_id = record["article_id"]
        try:
            _validate_docx(article_id, 1)
        except Exception as exc:
            store.delete(article_id)
            raise _reject_bad_docx(exc) from exc
        return _created_payload(record, 1)


@app.post("/api/manuscripts/{article_id}/versions", status_code=201, summary="上传新版稿件")
def create_version(
    article_id: str,
    role: workflow.Account,
    manuscript: Annotated[UploadFile, File(description="新版 Word 稿件（.docx）")],
    materials: Annotated[Optional[list[UploadFile]], File(description="新版参考材料，不传就沿用上一版")] = None,
    note: Annotated[Optional[str], Form(description="本版说明")] = None,
    title: Annotated[Optional[str], Form(description="新版标题")] = None,
) -> dict[str, Any]:
    workflow.ensure_author(_load_or_404(article_id, role), role)
    manuscript_name = safe_filename(manuscript.filename or "", "稿件.docx")
    manuscript_blob = _read_upload(manuscript)

    material_pairs: list[tuple[str, bytes]] | None = None
    if materials:
        material_pairs = [(safe_filename(item.filename or "", "材料.docx"), _read_upload(item)) for item in materials]

    def validate(article_id: str, version: int) -> None:
        try:
            _validate_docx(article_id, version)
        except Exception as exc:
            raise _reject_bad_docx(exc) from exc

    with store.lock:
        workflow.ensure_author(_load_or_404(article_id, role), role)
        record = store.add_version(
            article_id, manuscript_name, manuscript_blob, materials=material_pairs,
            note=(note or "").strip() or "上传新版", validate=validate, title=title,
        )
        return _created_payload(record, store.latest_version(record))


@app.get("/api/manuscripts", summary="稿件列表")
def list_manuscripts(role: workflow.Account, pending: bool = False) -> dict[str, Any]:
    with store.lock:
        records = [record for record in store.list_all() if workflow.can_read(record, role)]
    if pending:
        records = [
            record for record in records
            if role is not None and record.get("status") == "IN_REVIEW"
            and workflow.REVIEWERS.get(record.get("current_step")) == role
        ]
    return {"count": len(records), "articles": [_file_entry(record) for record in records]}


@app.get("/api/manuscripts/{article_id}", summary="稿件详情与审核记录")
def get_manuscript(article_id: str, role: workflow.Account) -> dict[str, Any]:
    with store.lock:
        record = _load_or_404(article_id, role)
        version = store.latest_version(record)
        preserved = store.load_check(article_id, version)
        return {
            **record,
            "latest_version": version,
            "paragraphs": read_docx_paragraphs(store.manuscript_path(article_id, version)),
            "issues": preserved["issues"] if preserved else [],
            "reviewed_return": preserved.get("reviewed_return") if preserved else None,
            "allowed_actions": workflow.allowed_actions(record, role),
        }


@app.post("/api/manuscripts/{article_id}/actions", summary="送审、通过、批注、退回、重提或签发")
def review_action(article_id: str, request: workflow.ActionRequest, role: workflow.Account) -> dict[str, Any]:
    if role is None:
        raise HTTPException(401, "请先登录")
    with store.lock:
        record = _load_or_404(article_id, role)
        version = store.latest_version(record)
        if request.version != version:
            raise HTTPException(409, "稿件版本已变化，请刷新")
        if request.action not in workflow.allowed_actions(record, role):
            raise HTTPException(403, "当前角色或审核环节不能执行此操作")
        if request.action == "RETURN" or request.paragraph_id is not None or request.quote is not None:
            paragraphs = read_docx_paragraphs(store.manuscript_path(article_id, version))
            text = paragraphs.get(request.paragraph_id or "", "")
            if not text or not ai_review.quote_in(request.quote, text):
                raise HTTPException(422, "请选择正文段落和可定位的原句")
        workflow.transition(record, role, request, now_iso())
        if request.action == "SIGN":
            signers = {
                event["step"]: event["actor"] for event in record["timeline"]
                if event["action"] in ("PASS", "SIGN")
            }
            document = Document(store.manuscript_path(article_id, version))
            document.add_paragraph(
                f"初审：{signers['FIRST']}　复审：{signers['SECOND']}　终审：{signers['FINAL']}"
            )
            signed = store.signed_path(article_id, version)
            temporary = signed.with_suffix(".tmp")
            document.save(temporary)
            temporary.replace(signed)
            record["signers"] = signers
        store.save_record(record)
        return get_manuscript(article_id, role)


@app.get("/api/manuscripts/{article_id}/signed", summary="下载锁定的签发版")
def download_signed(article_id: str, role: workflow.Account) -> FileResponse:
    record = _load_or_404(article_id, role)
    version = record.get("signed_version")
    if version is None:
        raise HTTPException(404, "稿件尚未签发")
    return FileResponse(
        store.signed_path(article_id, version),
        filename=f"{safe_filename(record['title'], '稿件')}-签发版.docx",
        media_type="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    )


@app.post(
    "/api/manuscripts/{article_id}/versions/{version}/check",
    summary="一键 AI 检查（指定版本）",
)
def check_version(article_id: str, version: int, role: workflow.Account) -> dict[str, Any]:
    return _run_check(article_id, version, role)


@app.post("/api/manuscripts/{article_id}/check", summary="一键 AI 检查（最新版）")
def check_latest(article_id: str, role: workflow.Account) -> dict[str, Any]:
    record = _load_or_404(article_id, role)
    version = store.latest_version(record)
    if version == 0:
        raise HTTPException(status_code=404, detail=f"稿件 {article_id} 还没有任何版本。")
    return _run_check(article_id, version, role)


@app.get(
    "/api/manuscripts/{article_id}/versions/{version}/check",
    summary="取回该版本上次的 AI 批注",
)
def get_check(article_id: str, version: int, role: workflow.Account) -> dict[str, Any]:
    record = _load_or_404(article_id, role)
    _version_or_404(record, version)
    payload = store.load_check(article_id, version)
    if payload is None:
        raise HTTPException(status_code=404, detail=f"第 {version} 版还没有跑过 AI 检查。")
    return payload


@app.get("/api/manuscripts/{article_id}/versions/{version}/paragraphs", summary="查询历史版本原文")
def get_version_paragraphs(article_id: str, version: int, role: workflow.Account) -> dict[str, str]:
    record = _load_or_404(article_id, role)
    _version_or_404(record, version)
    return read_docx_paragraphs(store.manuscript_path(article_id, version))


@app.get(
    "/api/manuscripts/{article_id}/versions/{version}/manuscript",
    summary="下载该版本稿件原件",
)
def download_manuscript(article_id: str, version: int, role: workflow.Account) -> FileResponse:
    record = _load_or_404(article_id, role)
    entry = _version_or_404(record, version)
    path = store.manuscript_path(article_id, version)
    return FileResponse(path, filename=entry["manuscript"]["name"])


@app.get(
    "/api/manuscripts/{article_id}/versions/{version}/materials/{index}",
    summary="下载该版本的第 index 份参考材料（从 0 开始）",
)
def download_material(article_id: str, version: int, index: int, role: workflow.Account) -> FileResponse:
    record = _load_or_404(article_id, role)
    entry = _version_or_404(record, version)
    materials = entry["materials"]
    if index < 0 or index >= len(materials):
        raise HTTPException(status_code=404, detail=f"第 {version} 版没有第 {index} 份参考材料。")
    path = store.material_paths(article_id, version)[index]
    return FileResponse(path, filename=materials[index]["name"])

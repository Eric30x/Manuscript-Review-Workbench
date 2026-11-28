"""演示账号和三审状态规则。"""

import hashlib
import hmac
import time
from typing import Annotated, Any

from fastapi import Depends, Header, HTTPException
from pydantic import BaseModel, Field

from . import config

ACCOUNTS = {
    "WRITER": "林小舟",
    "REVIEW_L1": "沈佳",
    "REVIEW_L2": "顾言",
    "FINAL": "郑毅",
}
STEPS = ("FIRST", "SECOND", "FINAL")
REVIEWERS = dict(zip(STEPS, ("REVIEW_L1", "REVIEW_L2", "FINAL")))
SESSION_SECONDS = 8 * 60 * 60


class LoginRequest(BaseModel):
    role: str
    password: str = Field(max_length=256)


class ActionRequest(BaseModel):
    action: str
    version: int = Field(ge=1)
    note: str = Field(default="", max_length=2000)
    paragraph_id: str | None = None
    quote: str | None = Field(default=None, max_length=4000)
    channel: str = "SCHOOL_SITE"


def _signature(message: str, password: str) -> str:
    return hmac.new(password.encode(), message.encode(), hashlib.sha256).hexdigest()


def login(request: LoginRequest) -> dict[str, str]:
    password = config.demo_password()
    if not password:
        raise HTTPException(503, "后端尚未配置 WORKBENCH_DEMO_PASSWORD")
    if request.role not in ACCOUNTS or not hmac.compare_digest(request.password.encode(), password.encode()):
        raise HTTPException(401, "演示角色或口令有误")
    message = f"{request.role}.{int(time.time()) + SESSION_SECONDS}"
    return {"token": f"{message}.{_signature(message, password)}", "name": ACCOUNTS[request.role]}


def account(authorization: Annotated[str | None, Header()] = None) -> str | None:
    if authorization is None:
        return None
    scheme, _, token = authorization.partition(" ")
    role, _, signed = token.partition(".")
    expiration, _, signature = signed.partition(".")
    password = config.demo_password()
    if (
        scheme.lower() != "bearer"
        or not password
        or role not in ACCOUNTS
        or not expiration.isascii()
        or not expiration.isdecimal()
        or len(expiration) > 12
        or int(expiration) <= time.time()
        or not hmac.compare_digest(signature.encode(), _signature(f"{role}.{expiration}", password).encode())
    ):
        raise HTTPException(401, "会话无效，请重新进入角色")
    return role


Account = Annotated[str | None, Depends(account)]


def can_read(record: dict[str, Any], role: str | None) -> bool:
    owner = record.get("owner")
    if not owner:
        return role is None and not config.demo_password()
    return role == owner or (role in REVIEWERS.values() and record["status"] != "DRAFT")


def ensure_read(record: dict[str, Any], role: str | None) -> None:
    if not can_read(record, role):
        raise HTTPException(401 if role is None else 403, "当前角色不能访问这篇稿件")


def ensure_author(record: dict[str, Any], role: str | None) -> None:
    ensure_read(record, role)
    if role != record.get("owner"):
        raise HTTPException(403, "只有作者能上传新版")
    if record.get("status", "DRAFT") not in ("DRAFT", "RETURNED"):
        raise HTTPException(409, "只有草稿或已退回稿件可以上传新版")


def allowed_actions(record: dict[str, Any], role: str | None) -> list[str]:
    if role is None:
        return []
    status = record["status"]
    step = record["current_step"]
    if role == record.get("owner") and status == "DRAFT":
        return ["SUBMIT"]
    if role == record.get("owner") and status == "RETURNED":
        return ["RESUBMIT"]
    if status == "IN_REVIEW" and role == REVIEWERS.get(step):
        return ["RETURN", "COMMENT", "SIGN" if step == "FINAL" else "PASS"]
    return []


def latest_return(record: dict[str, Any]) -> dict[str, Any] | None:
    return next((event for event in reversed(record.get("timeline", [])) if event["action"] == "RETURN"), None)


def transition(
    record: dict[str, Any], role: str, request: ActionRequest, timestamp: str,
) -> dict[str, Any]:
    """变更当前稿件，并把动作、位置和版本保存在同一条记录中。"""
    action = request.action
    if action not in allowed_actions(record, role):
        raise HTTPException(403, "当前角色或审核环节不能执行此操作")
    version = request.version
    step = record["current_step"]
    note = request.note.strip()
    if action in ("RETURN", "COMMENT") and not note:
        raise HTTPException(422, "请填写审核意见")

    event = {
        "action": action, "role": role, "actor": ACCOUNTS[role], "at": timestamp,
        "version": version, "step": step, "note": note,
        "paragraph_id": request.paragraph_id, "quote": request.quote,
    }
    if action in ("SUBMIT", "RESUBMIT"):
        if request.channel not in ("COLLEGE_WECHAT", "SCHOOL_SITE", "INNER_NOTICE"):
            raise HTTPException(422, "发布渠道无效")
        returned = latest_return(record)
        if action == "RESUBMIT" and (returned is None or version <= returned["version"]):
            raise HTTPException(409, "请先上传退回后的新版稿件")
        step = "FIRST" if action == "SUBMIT" else returned["step"]
        record.update(status="IN_REVIEW", current_step=step, channel=request.channel, return_note=None)
        event["step"] = step
    elif action == "RETURN":
        record.update(status="RETURNED", return_note=note)
    elif action == "PASS":
        record["passed_steps"].append(step)
        record["current_step"] = STEPS[STEPS.index(step) + 1]
    elif action == "SIGN":
        record["passed_steps"].append(step)
        record.update(status="SIGNED", current_step=None, signed_version=version)
    if action in ("RETURN", "COMMENT"):
        record["comments"].append({**event, "id": str(len(record["timeline"]) + 1)})
    record["timeline"].append(event)
    record["updated_at"] = timestamp
    return event

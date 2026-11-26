"""后端配置：只从环境变量 / backend/.env 读，代码里不写死任何密钥。

约定（与队长给的接口一致）：
- DEEPSEEK_API_KEY    必填。DeepSeek 的 API Key，也可以写进 backend/.env。
- DEEPSEEK_BASE_URL   可选，默认 https://api.deepseek.com ；换成学校网关就填网关地址。
- DEEPSEEK_TIMEOUT    可选，单次 AI 调用超时秒数，默认 120。
- WORKBENCH_DATA_DIR  可选，稿件与材料的落盘目录，默认 backend/data。
"""

from __future__ import annotations

import os
from pathlib import Path

from dotenv import load_dotenv

BACKEND_ROOT = Path(__file__).resolve().parents[1]

# backend/.env 存在就自动加载，省得每次开终端都手动 set。
load_dotenv(BACKEND_ROOT / ".env")

DEFAULT_BASE_URL = "https://api.deepseek.com"
DEFAULT_TIMEOUT = 120.0


def _env(name: str, default: str = "") -> str:
    return (os.getenv(name) or default).strip()


def api_key() -> str:
    return _env("DEEPSEEK_API_KEY")


def base_url() -> str:
    return _env("DEEPSEEK_BASE_URL", DEFAULT_BASE_URL).rstrip("/")


def timeout() -> float:
    try:
        return float(_env("DEEPSEEK_TIMEOUT", str(DEFAULT_TIMEOUT)))
    except ValueError:
        return DEFAULT_TIMEOUT


def data_dir() -> Path:
    raw = _env("WORKBENCH_DATA_DIR")
    return Path(raw).expanduser().resolve() if raw else BACKEND_ROOT / "data"

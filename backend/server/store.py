"""稿件与参考材料的本地存储层（MVP：目录 + JSON，不引数据库）。

设计约束来自队长给的接口约定：
- 参考材料必须**按原文件名**保存。ai/ai_review.py 用 path.name 当 materials 字典的键，
  所以落盘文件名 = 前端展示的「这条证据来自 XXX.docx」。
- 一份稿件可以有多个版本，每个版本各自带一份稿件与一组参考材料。

以后要换 SQLite / 对象存储，只要保持这里的方法签名不变，server/main.py 不用改。

落盘结构::

    <root>/articles/<article_id>/
        article.json                      稿件元数据（标题、各版本清单）
        versions/1/manuscript/<原文件名>    第 1 版的稿件
        versions/1/materials/<原文件名>     第 1 版的参考材料（可多份）
        versions/1/check.json             第 1 版最近一次的 AI 批注
"""

from __future__ import annotations

import hashlib
import json
import re
import secrets
import shutil
from datetime import datetime
from pathlib import Path
from typing import Any, Sequence

_ID_PATTERN = re.compile(r"^ms-\d{8}-\d{6}-[0-9a-f]{4}$")
# Windows / POSIX 都不允许出现在文件名里的字符：路径分隔符、通配符与控制字符。
_ILLEGAL_CHARS = re.compile(r'[\\/:*?"<>|\x00-\x1f]')


class NotFound(Exception):
    """稿件、版本或批注不存在。"""


def now_iso() -> str:
    """本地时区的 ISO 时间，秒级精度，够前端展示。"""
    return datetime.now().astimezone().isoformat(timespec="seconds")


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def safe_filename(name: str, fallback: str) -> str:
    """去掉路径成分与非法字符，保留中文原名 —— 展示用的来源名就是它。"""
    cleaned = _ILLEGAL_CHARS.sub("_", (name or "").strip())
    cleaned = cleaned.strip(" .")
    return cleaned or fallback


def _unique_name(name: str, taken: set[str]) -> str:
    """同一版本里材料重名时加 (2)(3) 后缀，避免互相覆盖。"""
    if name not in taken:
        return name
    head, dot, tail = name.rpartition(".")
    if not dot:
        head, tail = name, ""
    index = 2
    while f"{head} ({index}){dot}{tail}" in taken:
        index += 1
    return f"{head} ({index}){dot}{tail}"


def _file_info(path: Path) -> dict[str, Any]:
    return {"name": path.name, "size": path.stat().st_size, "sha256": sha256_of(path)}


class ArticleStore:
    def __init__(self, root: Path) -> None:
        self.root = Path(root)
        self.articles_dir = self.root / "articles"

    # ---------------------------------------------------------------- 路径

    def _new_id(self) -> str:
        stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
        return f"ms-{stamp}-{secrets.token_hex(2)}"

    def _check_id(self, article_id: str) -> str:
        if not _ID_PATTERN.match(article_id or ""):
            raise NotFound(f"稿件 ID 格式不正确：{article_id}")
        return article_id

    def article_dir(self, article_id: str) -> Path:
        return self.articles_dir / self._check_id(article_id)

    def version_dir(self, article_id: str, version: int) -> Path:
        return self.article_dir(article_id) / "versions" / str(int(version))

    def manuscript_path(self, article_id: str, version: int) -> Path:
        folder = self.version_dir(article_id, version) / "manuscript"
        files = sorted(item for item in folder.glob("*") if item.is_file()) if folder.exists() else []
        if not files:
            raise NotFound(f"稿件 {article_id} 的第 {version} 版没有稿件文件。")
        return files[0]

    def material_paths(self, article_id: str, version: int) -> list[Path]:
        folder = self.version_dir(article_id, version) / "materials"
        if not folder.exists():
            return []
        return sorted(item for item in folder.glob("*") if item.is_file())

    def check_path(self, article_id: str, version: int) -> Path:
        return self.version_dir(article_id, version) / "check.json"

    # ---------------------------------------------------------------- 建稿

    def create(
        self,
        manuscript_name: str,
        manuscript_bytes: bytes,
        materials: Sequence[tuple[str, bytes]],
        title: str | None = None,
    ) -> dict[str, Any]:
        """新建稿件并落第 1 版，返回稿件元数据。"""
        article_id = self._new_id()
        self.article_dir(article_id).mkdir(parents=True, exist_ok=True)
        self._write_version_files(article_id, 1, manuscript_name, manuscript_bytes, materials)

        record: dict[str, Any] = {
            "article_id": article_id,
            "title": (title or "").strip() or Path(manuscript_name).stem or "未命名稿件",
            "created_at": now_iso(),
            "updated_at": now_iso(),
            "versions": [self._version_entry(article_id, 1, "初次上传")],
        }
        self._write_record(record)
        return record

    def add_version(
        self,
        article_id: str,
        manuscript_name: str,
        manuscript_bytes: bytes,
        materials: Sequence[tuple[str, bytes]] | None = None,
        note: str = "上传新版",
    ) -> dict[str, Any]:
        """加一版。materials 传 None 表示沿用上一版的材料（改稿时不必重传）。"""
        record = self.load(article_id)
        version = self.latest_version(record) + 1
        pairs = list(materials) if materials is not None else self._inherit_materials(record)
        self._write_version_files(article_id, version, manuscript_name, manuscript_bytes, pairs)
        record["versions"].append(self._version_entry(article_id, version, note))
        record["updated_at"] = now_iso()
        self._write_record(record)
        return record

    def _inherit_materials(self, record: dict[str, Any]) -> list[tuple[str, bytes]]:
        version = self.latest_version(record)
        if version == 0:
            return []
        return [
            (path.name, path.read_bytes())
            for path in self.material_paths(record["article_id"], version)
        ]

    def _write_version_files(
        self,
        article_id: str,
        version: int,
        manuscript_name: str,
        manuscript_bytes: bytes,
        materials: Sequence[tuple[str, bytes]],
    ) -> None:
        base = self.version_dir(article_id, version)
        manuscript_dir = base / "manuscript"
        materials_dir = base / "materials"
        manuscript_dir.mkdir(parents=True, exist_ok=True)
        materials_dir.mkdir(parents=True, exist_ok=True)

        (manuscript_dir / safe_filename(manuscript_name, "稿件.docx")).write_bytes(manuscript_bytes)

        taken: set[str] = set()
        for name, blob in materials:
            unique = _unique_name(safe_filename(name, "材料.docx"), taken)
            taken.add(unique)
            (materials_dir / unique).write_bytes(blob)

    def _version_entry(self, article_id: str, version: int, note: str) -> dict[str, Any]:
        # 文件已落盘，这里回读真实文件名与大小，保证元数据和磁盘一致。
        manuscript = self.manuscript_path(article_id, version)
        materials = self.material_paths(article_id, version)
        return {
            "version": version,
            "note": note,
            "created_at": now_iso(),
            "manuscript": _file_info(manuscript),
            "materials": [_file_info(path) for path in materials],
            "checked": False,
            "last_checked_at": None,
            "issue_count": None,
        }

    def _write_record(self, record: dict[str, Any]) -> None:
        path = self.article_dir(record["article_id"]) / "article.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(record, ensure_ascii=False, indent=2), encoding="utf-8")

    # ---------------------------------------------------------------- 读取

    def load(self, article_id: str) -> dict[str, Any]:
        path = self.article_dir(article_id) / "article.json"
        if not path.exists():
            raise NotFound(f"找不到稿件：{article_id}")
        return json.loads(path.read_text(encoding="utf-8"))

    def list_all(self) -> list[dict[str, Any]]:
        if not self.articles_dir.exists():
            return []
        records: list[dict[str, Any]] = []
        for folder in sorted(item for item in self.articles_dir.iterdir() if item.is_dir()):
            meta = folder / "article.json"
            if not meta.is_file():
                continue
            try:
                records.append(json.loads(meta.read_text(encoding="utf-8")))
            except json.JSONDecodeError:
                continue
        records.sort(key=lambda item: item.get("created_at") or "", reverse=True)
        return records

    @staticmethod
    def latest_version(record: dict[str, Any]) -> int:
        return max((int(item["version"]) for item in record.get("versions", [])), default=0)

    def version_record(self, record: dict[str, Any], version: int) -> dict[str, Any]:
        for item in record.get("versions", []):
            if int(item["version"]) == int(version):
                return item
        raise NotFound(f"稿件 {record.get('article_id')} 没有第 {version} 版。")

    # ---------------------------------------------------------------- 批注

    def save_check(self, article_id: str, version: int, payload: dict[str, Any]) -> None:
        self.check_path(article_id, version).write_text(
            json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
        )
        record = self.load(article_id)
        entry = self.version_record(record, version)
        entry["checked"] = True
        entry["last_checked_at"] = payload.get("checked_at")
        entry["issue_count"] = payload.get("issue_count", 0)
        record["updated_at"] = now_iso()
        self._write_record(record)

    def load_check(self, article_id: str, version: int) -> dict[str, Any] | None:
        path = self.check_path(article_id, version)
        if not path.exists():
            return None
        return json.loads(path.read_text(encoding="utf-8"))

    # ---------------------------------------------------------------- 删除

    def delete(self, article_id: str) -> None:
        """只在上传校验失败时回滚用：删掉刚建的那个稿件目录。"""
        target = self.article_dir(article_id)
        if target.parent != self.articles_dir:
            raise ValueError(f"拒绝删除不安全的路径：{target}")
        shutil.rmtree(target, ignore_errors=True)

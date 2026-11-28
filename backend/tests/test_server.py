"""后端接口自测：上传 + AI 检查。默认用假 AI 结果，不花 token、不连网。

跑法（在 backend 目录下）::

    python -m pytest -q

想真连一次 DeepSeek，另开一档::

    set RUN_LIVE_AI=1
    python -m pytest -q -k live
"""

from __future__ import annotations

import io
import os
import sys
from pathlib import Path

import pytest
from docx import Document
from fastapi.testclient import TestClient

BACKEND_ROOT = Path(__file__).resolve().parents[1]
if str(BACKEND_ROOT) not in sys.path:
    sys.path.insert(0, str(BACKEND_ROOT))

from server import main as server_main  # noqa: E402
from server.store import ArticleStore  # noqa: E402

ORIGINAL_BUILD_CLIENT = server_main.build_client

MANUSCRIPT_PARAGRAPHS = [
    "校园开放日活动侧记",
    "11月20日，校园开放日在中心广场举行，共吸引约200人到场参与。",
]
MATERIAL_ROWS = [["序号", "姓名"], ["1", "林小舟"], ["2", "沈佳"]]


def docx_bytes(paragraphs, table_rows=None) -> bytes:
    document = Document()
    for text in paragraphs:
        document.add_paragraph(text)
    if table_rows:
        table = document.add_table(rows=len(table_rows), cols=len(table_rows[0]))
        for row_index, row in enumerate(table_rows):
            for col_index, value in enumerate(row):
                table.cell(row_index, col_index).text = value
    buffer = io.BytesIO()
    document.save(buffer)
    return buffer.getvalue()


def fake_result() -> dict:
    return {
        "issues": [
            {
                "paragraph_id": "P2",
                "quote": "共吸引约200人到场参与",
                "reason": "材料显示报名 120 人、签到 86 人，都支持不了 200 人到场。",
                "evidence": [
                    {"source": "报名统计.docx", "quote": "线上报名合计：120人。"},
                    {"source": "签到汇总.docx", "quote": "现场实际签到人数：86人。"},
                ],
                "suggestion": "按签到汇总改成「现场签到86人」。",
            }
        ]
    }


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.delenv("WORKBENCH_DEMO_PASSWORD", raising=False)
    monkeypatch.setattr(server_main, "store", ArticleStore(tmp_path / "data"))
    monkeypatch.setattr(server_main, "build_client", lambda: object())
    monkeypatch.setattr(server_main, "review_docx_files", lambda *args, **kwargs: fake_result())
    return TestClient(server_main.app)


def upload(client, material_names=("报名统计.docx", "签到汇总.docx")) -> dict:
    files = [
        (
            "manuscript",
            (
                "稿件初稿.docx",
                docx_bytes(MANUSCRIPT_PARAGRAPHS),
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            ),
        )
    ]
    for name in material_names:
        files.append(("materials", (name, docx_bytes(["材料正文"], MATERIAL_ROWS), "application/octet-stream")))
    response = client.post("/api/manuscripts", files=files)
    assert response.status_code == 201, response.text
    return response.json()


def test_health_reports_ai_state(client):
    body = client.get("/health").json()
    assert body["status"] == "ok"
    assert body["base_url"].startswith("http")


def test_upload_creates_version_one_and_keeps_material_names(client):
    body = upload(client)
    assert body["version"] == 1
    assert body["article_id"].startswith("ms-")
    assert body["manuscript"]["name"] == "稿件初稿.docx"
    # 关键：参考材料保留原文件名，前端才能显示「这条证据来自签到汇总.docx」
    assert [item["name"] for item in body["materials"]] == ["报名统计.docx", "签到汇总.docx"]


def test_upload_rejects_non_docx(client):
    response = client.post(
        "/api/manuscripts",
        files={"manuscript": ("坏文件.docx", b"this is definitely not a docx", "application/octet-stream")},
    )
    assert response.status_code == 400
    assert "docx" in response.json()["detail"]
    assert client.get("/api/manuscripts").json()["count"] == 0


def test_check_hands_original_file_names_to_ai(client, monkeypatch):
    seen = {}

    def spy(dummy_client, manuscript_path, material_paths):
        seen["manuscript"] = Path(manuscript_path).name
        seen["materials"] = sorted(Path(item).name for item in material_paths)
        return fake_result()

    monkeypatch.setattr(server_main, "review_docx_files", spy)
    body = upload(client)
    response = client.post(f"/api/manuscripts/{body['article_id']}/versions/1/check")
    assert response.status_code == 200, response.text

    assert seen["manuscript"] == "稿件初稿.docx"
    assert seen["materials"] == ["报名统计.docx", "签到汇总.docx"]

    payload = response.json()
    assert payload["issue_count"] == 1
    assert payload["issues"][0]["evidence"][0]["source"] == "报名统计.docx"
    assert payload["paragraphs"]["P2"].startswith("11月20日")


def test_check_result_is_saved_and_replayable(client):
    body = upload(client)
    url = f"/api/manuscripts/{body['article_id']}/versions/1/check"
    assert client.post(url).status_code == 200
    again = client.get(url)
    assert again.status_code == 200
    assert again.json()["issues"] == fake_result()["issues"]

    detail = client.get(f"/api/manuscripts/{body['article_id']}").json()
    assert detail["versions"][0]["checked"] is True
    assert detail["versions"][0]["issue_count"] == 1


def test_check_latest_and_unknown_article(client):
    body = upload(client)
    assert client.post(f"/api/manuscripts/{body['article_id']}/check").status_code == 200
    assert client.post("/api/manuscripts/ms-20260101-000000-abcd/check").status_code == 404
    assert client.get(f"/api/manuscripts/{body['article_id']}/versions/9/check").status_code == 404


def test_second_version_can_reuse_materials(client):
    body = upload(client)
    article_id = body["article_id"]
    response = client.post(
        f"/api/manuscripts/{article_id}/versions",
        files={
            "manuscript": (
                "稿件二稿.docx",
                docx_bytes(MANUSCRIPT_PARAGRAPHS + ["按签到口径改成了现场签到86人。"]),
                "application/octet-stream",
            )
        },
    )
    assert response.status_code == 201, response.text
    second = response.json()
    assert second["version"] == 2
    assert [item["name"] for item in second["materials"]] == ["报名统计.docx", "签到汇总.docx"]


def test_check_without_api_key_returns_503(client, monkeypatch):
    monkeypatch.setattr(server_main, "build_client", ORIGINAL_BUILD_CLIENT)
    monkeypatch.setattr(server_main.config, "api_key", lambda: "")
    body = upload(client)
    response = client.post(f"/api/manuscripts/{body['article_id']}/check")
    assert response.status_code == 503


@pytest.mark.skipif(
    os.getenv("RUN_LIVE_AI") != "1" or not os.getenv("DEEPSEEK_API_KEY"),
    reason="真连 DeepSeek 需要 RUN_LIVE_AI=1 且已配置 DEEPSEEK_API_KEY",
)
def test_live_deepseek_smoke(tmp_path):
    from openai import OpenAI

    from ai.ai_review import review_docx_files as real_review

    docs = tmp_path / "docs"
    docs.mkdir()
    (docs / "稿件初稿.docx").write_bytes(docx_bytes(MANUSCRIPT_PARAGRAPHS))
    (docs / "签到汇总.docx").write_bytes(docx_bytes(["现场实际签到人数：86人。"], MATERIAL_ROWS))

    real_client = OpenAI(
        api_key=os.environ["DEEPSEEK_API_KEY"],
        base_url=os.environ.get("DEEPSEEK_BASE_URL", "https://api.deepseek.com"),
        timeout=120,
    )
    result = real_review(real_client, docs / "稿件初稿.docx", [docs / "签到汇总.docx"])
    assert isinstance(result.get("issues"), list)


def test_demo_workflow_persists_signed_version_and_historical_notes(client, monkeypatch):
    monkeypatch.setenv("WORKBENCH_DEMO_PASSWORD", "temporary-test-password")
    accounts = {}

    def login(role):
        response = client.post("/api/session", json={"role": role, "password": "temporary-test-password"})
        assert response.status_code == 200, response.text
        return {"Authorization": "Bearer " + response.json()["token"]}

    for role in ("WRITER", "REVIEW_L1", "REVIEW_L2", "FINAL"):
        accounts[role] = login(role)
    assert client.post("/api/session", json={"role": "FINAL", "password": "wrong"}).status_code == 401

    def act(role, action, version=1, **kwargs):
        return client.post(path + "/actions", headers=accounts[role],
                           json={"action": action, "version": version, **kwargs})

    quote = "共吸引约200人到场参与"
    material = docx_bytes(["线上报名合计：200人。"])
    attendance = docx_bytes(["现场实际签到人数：126人。"])
    submitted = client.post("/api/manuscripts", headers=accounts["WRITER"], files=[
        ("manuscript", ("稿件初稿.docx", docx_bytes(MANUSCRIPT_PARAGRAPHS))),
        ("materials", ("报名统计.docx", material)),
        ("materials", ("签到汇总.docx", attendance)),
    ])
    assert submitted.status_code == 201, submitted.text
    path = "/api/manuscripts/" + submitted.json()["article_id"]
    assert client.get(path).status_code == 401
    assert client.get(path, headers=accounts["REVIEW_L1"]).status_code == 403
    assert client.post(path + "/versions/1/check", headers=accounts["WRITER"]).status_code == 200
    assert act("WRITER", "SUBMIT").json()["current_step"] == "FIRST"
    assert act("FINAL", "SIGN").status_code == 403
    pending = client.get("/api/manuscripts?pending=true", headers=accounts["REVIEW_L1"]).json()
    assert pending["count"] == 1
    assert act("REVIEW_L1", "PASS").json()["current_step"] == "SECOND"
    assert act("REVIEW_L2", "RETURN", paragraph_id="P2", quote="无法定位", note="核对人数").status_code == 422
    sent_back = act("REVIEW_L2", "RETURN", paragraph_id="P2", quote=quote, note="请区分报名和签到人数")
    assert sent_back.status_code == 200, sent_back.text
    assert sent_back.json()["comments"][-1]["quote"] == quote
    assert act("WRITER", "RESUBMIT").status_code == 409

    corrupt = client.post(path + "/versions", headers=accounts["WRITER"],
                          files={"manuscript": ("坏稿.docx", b"invalid")})
    assert corrupt.status_code == 400
    assert client.get(path, headers=accounts["WRITER"]).json()["latest_version"] == 1
    corrected = "校园开放日共有200人报名，126人签到。"
    revision = client.post(path + "/versions", headers=accounts["WRITER"],
                           files={"manuscript": ("稿件新版.docx", docx_bytes([corrected]))})
    assert revision.status_code == 201 and revision.json()["version"] == 2

    def recheck(_client, _manuscript, materials, *, review_note):
        assert review_note == "请区分报名和签到人数"
        assert sorted(path.name for path in materials) == ["报名统计.docx", "签到汇总.docx"]
        return {"issues": []}

    monkeypatch.setattr(server_main, "review_docx_files", recheck)
    checked = client.post(path + "/versions/2/check", headers=accounts["WRITER"])
    assert checked.status_code == 200, checked.text
    assert checked.json()["issues"] == [] and checked.json()["reviewed_return"]["version"] == 1
    assert client.get(path + "/versions/1/check", headers=accounts["WRITER"]).json()["issue_count"] == 1
    assert act("WRITER", "RESUBMIT", 2).json()["current_step"] == "SECOND"
    assert act("REVIEW_L1", "PASS", 2).status_code == 403
    assert act("REVIEW_L2", "PASS", 2).json()["current_step"] == "FINAL"
    assert act("FINAL", "SIGN", 2).json()["signed_version"] == 2

    # 创建新的存储实例，模拟同一数据目录的服务重启。
    monkeypatch.setattr(server_main, "store", ArticleStore(server_main.store.root))
    detail = client.get(path, headers=accounts["WRITER"]).json()
    assert detail["status"] == "SIGNED" and detail["comments"][0]["version"] == 1
    assert detail["paragraphs"]["P1"] == corrected
    history = client.get(path + "/versions/1/paragraphs", headers=accounts["REVIEW_L2"])
    assert quote in history.json()["P2"]
    downloaded = client.get(path + "/signed", headers=accounts["WRITER"])
    assert downloaded.status_code == 200
    body = [p.text for p in Document(io.BytesIO(downloaded.content)).paragraphs]
    assert body == [corrected, "初审：沈佳　复审：顾言　终审：郑毅"]
    assert client.post(path + "/versions/2/check", headers=accounts["WRITER"]).status_code == 409

"""HTTP 层测试：内部令牌校验与健康检查。

用 httpx 的 ASGITransport 直接打 FastAPI 应用，不起真实端口、不联网。
重点验证「未配置令牌时拒绝一切」这条安全默认值——配置漏了绝不能被解释成「完全开放」。
"""

from __future__ import annotations

import asyncio

import httpx
import pytest

from app.config import settings
from app.main import app
from app.security import HEADER_NAME


def request(method: str, path: str, **kwargs) -> httpx.Response:
    async def run():
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            return await client.request(method, path, **kwargs)

    return asyncio.run(run())


@pytest.fixture
def token(monkeypatch):
    monkeypatch.setattr(settings, "carcare_internal_token", "test-token")
    return "test-token"


def test_healthz_needs_no_token():
    resp = request("GET", "/healthz")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] in {"ok", "degraded"}
    assert "ragChunks" in body
    # 健康检查只暴露状态，不该泄漏密钥之类的配置
    assert "key" not in resp.text.lower()


def test_chat_requires_token(token):
    resp = request("POST", "/v1/chat", json={"sessionId": "s", "message": "你好"})
    assert resp.status_code == 401


def test_chat_rejects_wrong_token(token):
    resp = request("POST", "/v1/chat",
                   json={"sessionId": "s", "message": "你好"},
                   headers={HEADER_NAME: "wrong-token"})
    assert resp.status_code == 401


def test_rag_search_requires_token(token):
    resp = request("GET", "/v1/rag/search", params={"q": "刹车片"})
    assert resp.status_code == 401


def test_rag_reload_requires_token(token):
    assert request("POST", "/v1/rag/reload").status_code == 401


def test_empty_configured_token_denies_everything(monkeypatch):
    """没配令牌时必须整体不可用，而不是变成「谁都能调」。"""
    monkeypatch.setattr(settings, "carcare_internal_token", "")
    resp = request("POST", "/v1/chat", json={"sessionId": "s", "message": "你好"},
                   headers={HEADER_NAME: ""})
    assert resp.status_code == 500


def test_chat_validates_body(token):
    resp = request("POST", "/v1/chat", json={"sessionId": "s", "message": ""},
                   headers={HEADER_NAME: token})
    assert resp.status_code == 422


def test_rag_search_with_token_returns_scored_hits(token):
    """真实调用 embedding 检索；未配置密钥时跳过，避免 CI 无网必失败。"""
    if not settings.rag_available:
        pytest.skip("未启用 RAG（缺 EMBEDDING_API_KEY）")
    resp = request("GET", "/v1/rag/search", params={"q": "刹车片什么时候必须换", "k": 3},
                   headers={HEADER_NAME: token})
    assert resp.status_code == 200
    body = resp.json()
    assert body["query"] == "刹车片什么时候必须换"
    if body["hits"]:
        assert body["hits"][0]["score"] > 0
        assert body["hits"][0]["text"].startswith("【")

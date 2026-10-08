"""Chroma 向量库：本地持久化，零额外运维。

embedding 用 SiliconFlow 的 bge-m3（OpenAI 兼容接口）。两个关键细节：
1. check_embedding_ctx_length=False —— OpenAIEmbeddings 默认会用 tiktoken 按 OpenAI 的
   tokenizer 预切分文本，bge-m3 不是 OpenAI 模型，开着的后果是编码对不上甚至直接报错；
2. 检索分数统一取 1-cosine_distance（LangChain 的 relevance score），再用 min_score 卡阈值，
   低于阈值的结果直接丢弃——宁可让模型说「不知道」，也不要塞一段不相关的资料诱导它编。

检索失败一律降级为空列表：向量库挂了只损失 RAG 能力，对话本身必须继续可用。
"""

from __future__ import annotations

import logging
import threading

import chromadb
from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_openai import OpenAIEmbeddings

from app.config import settings

log = logging.getLogger(__name__)

_lock = threading.Lock()
_store: Chroma | None = None


def _build_embeddings() -> OpenAIEmbeddings:
    return OpenAIEmbeddings(
        model=settings.embedding_model,
        api_key=settings.embedding_api_key,
        base_url=settings.embedding_base_url,
        check_embedding_ctx_length=False,
    )


def _build_store() -> Chroma:
    settings.chroma_path.mkdir(parents=True, exist_ok=True)
    return Chroma(
        collection_name=settings.chroma_collection,
        embedding_function=_build_embeddings(),
        persist_directory=str(settings.chroma_path),
    )


def get_store() -> Chroma | None:
    """惰性构建单例；构建失败返回 None（调用方按「无 RAG」处理）。"""
    global _store
    if not settings.rag_available:
        return None
    if _store is None:
        with _lock:
            if _store is None:
                try:
                    _store = _build_store()
                except Exception as e:
                    log.warning("向量库初始化失败，本次按无 RAG 处理: %s", e)
                    return None
    return _store


def count_chunks() -> int:
    store = get_store()
    if store is None:
        return 0
    try:
        return len(store.get(include=[])["ids"])
    except Exception as e:
        log.warning("读取向量库条数失败: %s", e)
        return 0


def search(query: str, k: int | None = None, min_score: float | None = None) -> list[tuple[Document, float]]:
    """相似度检索，返回 (文档, 相关度) 并按阈值过滤。任何异常都降级为空列表。"""
    store = get_store()
    if store is None or not query.strip():
        return []
    top_k = k or settings.rag_top_k
    threshold = settings.rag_min_score if min_score is None else min_score
    try:
        hits = store.similarity_search_with_relevance_scores(query, k=top_k)
    except Exception as e:
        log.warning("向量检索失败，本次按无 RAG 处理: %s", e)
        return []
    kept = [(doc, score) for doc, score in hits if score >= threshold]
    log.info("RAG 检索 q=%r 命中 %d/%d 条（阈值 %.2f）", query[:40], len(kept), len(hits), threshold)
    return kept


def rebuild(documents: list[Document]) -> int:
    """重建知识库：先删 collection 再灌入，保证幂等（重复执行不会累积重复分片）。"""
    global _store
    with _lock:
        if not settings.rag_available:
            log.warning("RAG 未启用或缺少 embedding 密钥，跳过重建")
            return 0
        # 用底层 chromadb client 删除，比依赖 langchain-chroma 的版本相关 API 更稳
        try:
            client = chromadb.PersistentClient(path=str(settings.chroma_path))
            client.delete_collection(name=settings.chroma_collection)
            log.info("已删除旧 collection: %s", settings.chroma_collection)
        except Exception as e:
            log.info("旧 collection 不存在或删除失败（首次构建属正常）: %s", e)
        _store = _build_store()
        if not documents:
            return 0
        # 分批写入：单次请求塞几百条文本会顶到 embedding 接口的体积上限
        batch = 32
        for i in range(0, len(documents), batch):
            _store.add_documents(documents[i:i + batch])
        total = count_chunks()
        log.info("知识库重建完成，共 %d 个分片", total)
        return total

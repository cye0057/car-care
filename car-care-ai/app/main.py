"""FastAPI 应用入口。

对外接口（全部只绑 127.0.0.1，唯一调用方是 car-care-server）：
- GET  /healthz          健康检查（不需要令牌，只暴露模型名与 RAG 状态）
- POST /v1/chat          流式对话（SSE），Java 逐帧透传给前端
- POST /v1/chat/sync     非流式对话，调试与单测用
- POST /v1/rag/reload    重建知识库（会消耗 embedding 额度，慎用）
- GET  /v1/rag/search    只检索不生成，排障用：先确认检索对不对，再怀疑提示词
"""

from __future__ import annotations

import asyncio
import logging
import sys
from contextlib import asynccontextmanager
from pathlib import Path

# 支持「直接运行本文件」（IDE 的绿色启动按钮 / python app/main.py）：
# 这种启动方式下 sys.path[0] 是 app/ 而不是项目根，下面的 `from app import ...` 会报
# ModuleNotFoundError。用 `-m app.main` 或 uvicorn 启动时 __package__ 有值，不会走到这里。
if __package__ in (None, ""):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from fastapi import Depends, FastAPI, HTTPException, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from sse_starlette.sse import EventSourceResponse

from app import __version__  # noqa: E402
from app.agent.builder import get_agent  # noqa: E402
from app.clients.carcare import close_client  # noqa: E402
from app.config import settings  # noqa: E402
from app.rag import store as rag_store  # noqa: E402
from app.rag.ingest import load_chunks  # noqa: E402
from app.schemas import ChatRequest, HealthResponse, RagHit  # noqa: E402
from app.security import require_internal_token  # noqa: E402
from app.services.chat import chat_sync, stream_chat  # noqa: E402

log = logging.getLogger(__name__)


def _setup_logging() -> None:
    logging.basicConfig(
        level=getattr(logging, settings.log_level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)-5s [%(name)s] %(message)s",
        datefmt="%H:%M:%S",
    )


def _warm_up_rag() -> None:
    """启动时预热向量库；collection 为空则自动灌一次语料，避免首次使用查不到东西。"""
    if not settings.rag_available:
        log.warning("RAG 未启用（RAG_ENABLED=false 或缺 EMBEDDING_API_KEY），将只提供工具问答")
        return
    count = rag_store.count_chunks()
    if count == 0 and settings.rag_ingest_on_startup:
        log.info("向量库为空，开始灌入知识库语料…")
        count = rag_store.rebuild(load_chunks())
    log.info("RAG 就绪：collection=%s 分片数=%d", settings.chroma_collection, count)


@asynccontextmanager
async def lifespan(app: FastAPI):
    _setup_logging()
    log.info("car-care-ai v%s 启动：model=%s host=%s:%s",
             __version__, settings.llm_model, settings.host, settings.port)
    # 构建 Agent 与预热向量库都是阻塞操作（含网络调用），丢到线程里避免卡住事件循环启动
    await asyncio.to_thread(get_agent)
    await asyncio.to_thread(_warm_up_rag)
    try:
        yield
    finally:
        await close_client()
        log.info("car-care-ai 已停止")


app = FastAPI(
    title="车管家 AI 助手服务",
    description="基于 LangChain + FastAPI 的养车顾问服务，仅供 car-care-server 内部调用",
    version=__version__,
    lifespan=lifespan,
    docs_url="/docs",
)


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    """把请求体校验失败也打进日志。

    FastAPI 默认只返回 422 的 detail，不写日志——调用方（Java）只看到「422」，
    排查时完全不知道是哪个字段不对。这里把错误字段和原始请求体（截断）都记下来。
    """
    raw = (await request.body())[:2000]
    log.warning("请求体校验失败 %s %s errors=%s body=%s",
                request.method, request.url.path, exc.errors(), raw.decode("utf-8", "replace"))
    return JSONResponse(status_code=422, content={"detail": exc.errors()})


@app.get("/healthz", response_model=HealthResponse, summary="健康检查")
async def healthz() -> HealthResponse:
    chunks = await asyncio.to_thread(rag_store.count_chunks)
    rag_ready = settings.rag_available and chunks > 0
    detail = None
    if not settings.rag_available:
        detail = "RAG 未启用或缺少 embedding 密钥"
    elif chunks == 0:
        detail = "知识库为空，可调用 POST /v1/rag/reload 灌入语料"
    return HealthResponse(
        status="ok" if rag_ready else "degraded",
        model=settings.llm_model,
        ragReady=rag_ready,
        ragChunks=chunks,
        detail=detail,
    )


@app.post("/v1/chat", summary="流式对话（SSE）", dependencies=[Depends(require_internal_token)])
async def chat(req: ChatRequest) -> EventSourceResponse:
    async def event_generator():
        async for event in stream_chat(req):
            yield {"data": event.to_sse()}

    return EventSourceResponse(event_generator())


@app.post("/v1/chat/sync", summary="非流式对话（调试用）", dependencies=[Depends(require_internal_token)])
async def chat_sync_endpoint(req: ChatRequest) -> dict:
    return await chat_sync(req)


@app.post("/v1/rag/reload", summary="重建知识库", dependencies=[Depends(require_internal_token)])
async def rag_reload() -> dict:
    if not settings.rag_available:
        raise HTTPException(status_code=400, detail="RAG 未启用或缺少 embedding 密钥")
    chunks = await asyncio.to_thread(load_chunks)
    total = await asyncio.to_thread(rag_store.rebuild, chunks)
    return {"loaded": len(chunks), "stored": total}


@app.get("/v1/rag/search", summary="只检索不生成（排障用）",
         dependencies=[Depends(require_internal_token)])
async def rag_search(q: str = Query(min_length=1), k: int = Query(default=5, ge=1, le=20)) -> dict:
    hits = await asyncio.to_thread(rag_store.search, q, k, 0.0)
    return {
        "query": q,
        "threshold": settings.rag_min_score,
        "hits": [RagHit(text=doc.page_content, source=str(doc.metadata.get("source", "")), score=round(score, 4))
                 for doc, score in hits],
    }


if __name__ == "__main__":
    # 让 IDE 的绿色启动按钮（本质是 python app/main.py）也能起服务，
    # 等价于 python -m uvicorn app.main:app --host <HOST> --port <PORT>。
    # 传 app 对象而不是 "app.main:app" 字符串：脚本方式运行时本模块的名字是 __main__，
    # 用导入字符串会让 uvicorn 再导入一份 app.main，于是进程里有两个应用实例
    # （被服务的是后者，__main__ 那份的 lifespan 不跑，日志会看着很奇怪）。
    # 需要热重载（--reload）时请改用命令行形式。
    import uvicorn

    uvicorn.run(app, host=settings.host, port=settings.port)

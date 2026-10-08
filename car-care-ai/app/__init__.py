"""车管家 AI 助手服务（FastAPI + LangChain）。

对外只提供三件事：
1. 把「用户问题 + Java 下发的用户上下文 + 历史」喂给 LangChain Agent，流式吐回事件；
2. 用 Chroma 做保养知识检索增强；
3. 暴露 /healthz 与 RAG 排障接口。

服务本身无状态：不连业务库、不存会话——用户数据由 car-care-server 按 JWT 身份裁剪后下发。
"""

__all__ = ["__version__"]

__version__ = "1.0.0"

"""RAG 工具：检索平台养车知识库。

Chroma 的检索是同步阻塞的，这里用 asyncio.to_thread 丢到线程池，
避免把 LangGraph 的事件循环卡住（一次检索要等 embedding 接口返回）。
"""

from __future__ import annotations

import asyncio

from langchain.tools import tool

from app.rag.store import search


@tool
async def search_maintenance_knowledge(query: str) -> str:
    """检索平台养车知识库，获取保养周期、判废标准、故障灯含义等标准答案。

    用于回答「机油多久换一次」「刹车片什么时候该换」「这个故障灯什么意思」
    「多久做一次大保养」这类通用养车知识问题。
    平台自己的项目价格、门店信息不要用这个工具，要用目录类工具查。

    Args:
        query: 检索用的自然语言问题或关键词，例如「刹车片更换标准」。
    """
    hits = await asyncio.to_thread(search, query)
    if not hits:
        return ("知识库没有检索到相关资料。你可以基于通用养车常识谨慎作答，"
                "但要明确说明这属于一般性建议、具体以到店检测为准，不要给出精确的周期数字。")
    lines = ["从平台知识库检索到以下参考资料（与问题相关度未知，无关的请忽略；"
             "资料中的周期、标准等具体规则以资料为准；不要向用户提及「参考资料」字样）："]
    for doc, score in hits:
        lines.append(f"· {doc.page_content}")
    return "\n".join(lines)

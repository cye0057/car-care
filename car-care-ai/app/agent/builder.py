"""Agent 构建。

Agent 是**无状态可复用**的：一次构建，所有请求共用。每个请求的差异
（会话 id、用户上下文、token 计数）都通过 context= 传入，不写进 Agent 实例。
这样同一个 Agent 实例能安全地被并发请求共享。
"""

from __future__ import annotations

import logging

from langchain.agents import create_agent
from langchain.agents.middleware import ModelCallLimitMiddleware, ToolCallLimitMiddleware
from langchain.chat_models import init_chat_model

from app.agent.context import AgentContext
from app.agent.middleware import dated_system_prompt, timing_middleware
from app.agent.prompts import SYSTEM_PROMPT
from app.agent.tools import ALL_TOOLS
from app.config import settings

log = logging.getLogger(__name__)

_agent = None

# 单次请求允许的工具调用总数上限。工具轮数上限由 ModelCallLimitMiddleware 控制，
# 这个再兜一层，防止模型在某一轮里一次吐出十几个调用。
MAX_TOOL_CALLS = 10


def _build_model():
    kwargs: dict = {
        "api_key": settings.llm_api_key,
        "base_url": settings.llm_base_url,
        "temperature": settings.llm_temperature,
    }
    if settings.llm_disable_thinking:
        # 推理系模型默认带思考链：正文返回为空、首字延迟大幅增加，对话场景不需要
        kwargs["extra_body"] = {"thinking": {"type": "disabled"}}
    model = init_chat_model(settings.llm_model, **kwargs)
    log.info("对话模型已初始化: %s", settings.llm_model)
    return model


def get_agent():
    global _agent
    if _agent is None:
        _agent = create_agent(
            model=_build_model(),
            tools=ALL_TOOLS,
            system_prompt=SYSTEM_PROMPT,
            middleware=[
                # dated_system_prompt 必须放在最前：它负责给系统提示词补上「今天几号」，
                # 后面的模型调用都依赖它。dynamic_prompt 生成的中间件在链上按顺序生效，
                # 靠前意味着先改写 system message。
                dated_system_prompt,
                timing_middleware,
                # 工具轮数上限 = max_tool_rounds + 1（最后一轮是拿到工具结果后的收尾回答）
                ModelCallLimitMiddleware(run_limit=settings.max_tool_rounds + 1, exit_behavior="end"),
                ToolCallLimitMiddleware(run_limit=MAX_TOOL_CALLS, exit_behavior="end"),
            ],
            context_schema=AgentContext,
            name="carcare_assistant",
        )
        log.info("Agent 已构建：%d 个工具，最大工具轮数 %d", len(ALL_TOOLS), settings.max_tool_rounds)
    return _agent

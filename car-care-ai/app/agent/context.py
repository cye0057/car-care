"""Agent 的请求级上下文。

LangChain 1.x 的 create_agent 支持 context_schema：调用时把 per-request 的上下文传进去，
工具通过 `runtime: ToolRuntime[AgentContext]` 拿到它。

这样做的好处是**工具不再依赖全局状态**——同一个 Agent 实例可以并发服务多个用户，
各自的 userId / 用户上下文互不干扰。langchain1.2 练习里的 SmartAssistant 把 messages
存在实例字段上，那种写法在多用户并发下会串会话。
"""

from __future__ import annotations

from dataclasses import dataclass

from app.schemas import UserContext


@dataclass
class AgentContext:
    session_id: str
    user_id: int | None = None
    user_context: UserContext | None = None

    # 以下由 TimingMiddleware 累计，随 done 事件返回给 Java 并落库。
    # 每次请求新建一个 AgentContext，因此这些计数天然是「本次请求」的，不会串。
    model_calls: int = 0
    input_tokens: int = 0
    output_tokens: int = 0
    llm_ms: int = 0

    @property
    def city(self) -> str | None:
        return self.user_context.city if self.user_context else None

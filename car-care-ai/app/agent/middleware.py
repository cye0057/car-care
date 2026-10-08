"""自定义中间件。

LangChain 1.x 的 Agent 中间件本质是挂在 LangGraph 节点上的钩子。
这里只用到一个自定义中间件（耗时/token 统计），其余预算控制直接复用框架自带的：
- ModelCallLimitMiddleware：模型调用次数上限 = 工具轮数 + 1，硬性截断空转；
- ToolCallLimitMiddleware：工具调用总数上限，防模型一次吐出十几个调用把成本打飞。

用框架自带的而不是自己写循环计数，是因为它们已经处理好了「截断后如何优雅收尾」
（exit_behavior="end" 会让 Agent 带着现有信息给出最终回答，而不是抛异常）。
"""

from __future__ import annotations

import logging
import time
from datetime import date

from langchain.agents.middleware import ModelRequest, ModelResponse, dynamic_prompt, wrap_model_call

from app.agent.context import AgentContext
from app.agent.prompts import SYSTEM_PROMPT

log = logging.getLogger(__name__)

_WEEKDAYS = ("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")


def _extract_usage(result: object) -> tuple[int, int]:
    """从 AIMessage 里取 token 用量。不同 provider 字段名不完全一致，全部做兜底。"""
    messages = result if isinstance(result, list) else [result]
    in_tokens = out_tokens = 0
    for msg in messages:
        usage = getattr(msg, "usage_metadata", None)
        if not isinstance(usage, dict):
            continue
        in_tokens += int(usage.get("input_tokens") or 0)
        out_tokens += int(usage.get("output_tokens") or 0)
    return in_tokens, out_tokens


@dynamic_prompt
def dated_system_prompt(request: ModelRequest) -> str:
    """在系统提示词里注入「今天几号」。

    不加这段会出真问题：用户说「这周六上午去」，模型不知道今天是几号，
    会凭训练语料的记忆编一个过去的日期（实测编出了 2026-06-27，而当天是 2026-09-24），
    生成的预约草稿直接是无效时间。注入之后相对时间才能正确换算。

    用 dynamic_prompt 而不是把日期写死在 SYSTEM_PROMPT 里，是因为提示词常量在进程启动时
    就固定了，长驻的服务第二天就会带着昨天的日期回答。
    """
    today = date.today()
    return (f"{SYSTEM_PROMPT}\n\n"
            f"当前日期：{today.isoformat()}（{_WEEKDAYS[today.weekday()]}）。\n"
            f"用户提到「今天/明天/后天/这周六/下周/月底」这类相对时间时，"
            f"必须基于上面这个日期换算成具体日期（格式 yyyy-MM-dd HH:mm），不要凭记忆推测年份和月份。")


@wrap_model_call(name="TimingMiddleware")
async def timing_middleware(request: ModelRequest, handler) -> ModelResponse:
    """累计模型调用次数、耗时与 token 用量，写回本次请求的 AgentContext。

    这些数字会随 done 事件返回给 Java 并落库，用于回答「一次对话到底花了多少钱、慢在哪」——
    travel 项目没做这件事，事后只能靠 JMeter 的总耗时反推。
    """
    context = getattr(request.runtime, "context", None)
    started = time.perf_counter()
    response = await handler(request)
    elapsed_ms = int((time.perf_counter() - started) * 1000)

    if isinstance(context, AgentContext):
        in_tokens, out_tokens = _extract_usage(response.result)
        context.model_calls += 1
        context.llm_ms += elapsed_ms
        context.input_tokens += in_tokens
        context.output_tokens += out_tokens
        log.info("模型调用 #%d 耗时 %dms tokens=%d/%d",
                 context.model_calls, elapsed_ms, in_tokens, out_tokens)
    return response

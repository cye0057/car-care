"""对话编排：把「历史 + 用户问题 + 用户上下文」喂给 Agent，把输出归一化成 SSE 事件。

这个模块承担三件事，也是整个服务里最容易出错的地方：

1. **把 LangGraph 的消息流翻译成前端能懂的事件**
   agent.astream(..., stream_mode="messages") 吐的是 (message_chunk, metadata)，
   需要按 metadata["langgraph_node"] 区分「模型在说话」还是「工具在返回结果」。

2. **处理工具轮里的过渡语**
   模型在调用工具前常会先说一句「让我查一下门店」。如果直接透传，
   用户会看到这句废话残留在回答里。这里在检测到某轮出现工具调用时发一个 reset 事件，
   让前端把这一轮已经渲染的文本清掉——既保留了真流式（不用等生成完才吐字），
   又不会把中间态留在最终回答里。

3. **预算与取消**
   整个流程包在 asyncio.timeout 里，超时主动断流并告知用户；
   客户端断开时 CancelledError 直接向上抛，LangGraph 会取消这次 run，
   上游模型请求随之终止（不会出现「前端断了后端还在烧 token」）。
"""

from __future__ import annotations

import asyncio
import json
import logging
import time
from collections.abc import AsyncIterator

from langchain_core.messages import AIMessage, AIMessageChunk, BaseMessage, HumanMessage, ToolMessage

from app.agent.builder import get_agent
from app.agent.context import AgentContext
from app.agent.prompts import label_for
from app.config import settings
from app.schemas import ChatEvent, ChatRequest

log = logging.getLogger(__name__)

_DRAFT_MARKER = "__DRAFT__"


def _to_text(content: object) -> str:
    """把消息内容统一成纯文本。部分模型会返回 content blocks 列表而不是字符串。"""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for block in content:
            if isinstance(block, str):
                parts.append(block)
            elif isinstance(block, dict) and block.get("type") == "text":
                parts.append(str(block.get("text", "")))
        return "".join(parts)
    return "" if content is None else str(content)


def _build_messages(req: ChatRequest) -> list[BaseMessage]:
    """历史由 Java 持久化并在每次请求下发，这里只做拼装，不做裁剪。"""
    messages: list[BaseMessage] = []
    for turn in req.history:
        messages.append(HumanMessage(turn.content) if turn.role == "user" else AIMessage(turn.content))
    messages.append(HumanMessage(req.message))
    return messages


async def stream_chat(req: ChatRequest) -> AsyncIterator[ChatEvent]:
    """产出本次对话的全部事件。异常不会向外抛，统一转成 error 事件。"""
    context = AgentContext(session_id=req.sessionId, user_id=req.userId, user_context=req.userContext)
    started = time.perf_counter()
    # 当前这一轮模型输出的正文。检测到工具调用就整轮丢弃——那是「我这就去查」之类的过渡语，
    # 不该留在最终回答里。注意这个判断必须**逐轮**做：早先的写法用了一个「本轮是否已有工具调用」
    # 的布尔标志，置位后不复位，于是第二轮之后的过渡语就再也不会被清掉，
    # 最终回答里会混进一句英文自述（实测踩到过）。
    round_text: list[str] = []
    # 工具调用的参数是分片到达的，要按「调用」攒起来。
    # 键必须用工具调用 id 而不是 index：每一轮的 index 都从 0 重新开始，
    # 用 index 做键会让第二轮的调用撞上第一轮的残留（slot["name"] 已有值），
    # 于是 first_time 永远判不出来 —— reset 和 tool_start 都不再触发，
    # 最终回答里会累积各轮的过渡语（实测踩到过，症状是回答里全是英文自述）。
    pending_calls: dict[str, dict] = {}
    call_names_by_id: dict[str, str] = {}
    # 工具调用分片只有**第一个**分片带 id 与 name，后续分片只有 index + args（OpenAI 兼容格式）。
    # 所以必须把 index 映射回该调用的 key，否则参数会被攒进另一个槽位，
    # 表现出来就是 toolArgs 永远是空（实测踩到过）。
    round_keys: dict[int, str] = {}
    # 工具参数的完整值：tool_start 是在第一个分片就发出的（那时参数还没到齐，只能给出工具名），
    # 所以完整参数挂到 tool_end 上，前端与排障日志才有得看。
    call_args_by_id: dict[str, dict | None] = {}
    # 本轮是否产出了预约草稿。用于「模型没来得及写最终回答」时给一句有意义的收尾，
    # 而不是甩一句「抱歉没能生成回答」——草稿都出来了还说抱歉，用户会以为没成功。
    draft_ready = False

    yield ChatEvent(type="start")

    try:
        async with asyncio.timeout(settings.request_timeout):
            agent = get_agent()
            stream = agent.astream(
                {"messages": _build_messages(req)},
                context=context,
                stream_mode="messages",
            )
            async for chunk, metadata in stream:
                node = (metadata or {}).get("langgraph_node")

                if isinstance(chunk, AIMessageChunk):
                    # --- 1) 正文增量 ---
                    piece = _to_text(chunk.content)
                    if piece:
                        round_text.append(piece)
                        yield ChatEvent(type="delta", content=piece)

                    # --- 2) 工具调用分片 ---
                    for tc in getattr(chunk, "tool_call_chunks", None) or []:
                        index = tc.get("index") or 0
                        # 续接分片没有 id，用本轮 index→key 的映射找回同一个槽位
                        key = tc.get("id") or round_keys.get(index) or f"idx{index}"
                        if tc.get("id"):
                            round_keys[index] = key
                        slot = pending_calls.setdefault(key, {"name": None, "id": None, "args": ""})
                        if tc.get("id"):
                            slot["id"] = tc["id"]
                        if tc.get("args"):
                            slot["args"] += tc["args"]
                            # 参数分片到达，每次覆盖成「截至目前能解析出来的」，
                            # 最后一个分片到齐后即为完整参数。
                            call_args_by_id[key] = _safe_json(slot["args"])
                        first_time = slot["name"] is None and tc.get("name")
                        if tc.get("name"):
                            slot["name"] = tc["name"]
                        if first_time:
                            # 这一轮要调工具了 → 之前吐的过渡语作废，让前端清掉
                            if round_text:
                                yield ChatEvent(type="reset")
                                round_text.clear()
                            call_names_by_id[key] = slot["name"]
                            yield ChatEvent(
                                type="tool_start",
                                toolName=slot["name"],
                                toolLabel=label_for(slot["name"]),
                            )

                elif isinstance(chunk, ToolMessage):
                    # --- 3) 工具结果 ---
                    tool_call_id = getattr(chunk, "tool_call_id", None) or ""
                    name = getattr(chunk, "name", None) or call_names_by_id.get(tool_call_id, "unknown")
                    raw = _to_text(chunk.content)
                    ok = not _looks_failed(raw)
                    yield ChatEvent(
                        type="tool_end",
                        toolName=name,
                        toolLabel=label_for(name),
                        toolOk=ok,
                        toolSummary=_summarize(raw),
                        toolArgs=call_args_by_id.get(tool_call_id),
                    )
                    if name == "build_booking_draft":
                        draft = _extract_draft(raw)
                        if draft is not None:
                            draft_ready = True
                            yield ChatEvent(type="draft", draft=draft)
                    # 工具执行完代表这一轮结束，清掉累积状态，避免下一轮的 index/id 撞车
                    pending_calls.clear()
                    round_keys.clear()

                if log.isEnabledFor(logging.DEBUG):
                    log.debug("stream chunk node=%s type=%s", node, type(chunk).__name__)

        # 只保留最后一轮（未被工具调用作废的那一轮）的正文，就是最终回答
        answer = "".join(round_text).strip()
        if not answer:
            answer = _fallback_answer(draft_ready, bool(call_names_by_id))
            log.warning("模型未产出最终正文，使用兜底回答 session=%s draft=%s tools=%d",
                        req.sessionId, draft_ready, len(call_names_by_id))
        # 幻觉兜底：模型有时**不调用** build_booking_draft，却在回答里写「草稿已生成、点卡片确认预约」，
        # 用户于是去找一张根本不存在的卡片。提示词已写明「调用工具才算生成」，但那只是概率约束，
        # 这里再兜一层：声称有草稿而实际没产出，就补一句更正。
        if not draft_ready and _claims_draft(answer):
            log.warning("模型声称已生成预约草稿但未调用工具 session=%s", req.sessionId)
            correction = ("\n\n（更正：这次其实没有生成预约卡片，请把门店和要做的项目再说一次，我重新生成。）")
            yield ChatEvent(type="delta", content=correction)
            answer += correction
        yield ChatEvent(
            type="done",
            content=answer,
            latencyMs=int((time.perf_counter() - started) * 1000),
            inputTokens=context.input_tokens,
            outputTokens=context.output_tokens,
        )
        log.info("对话完成 session=%s 模型调用 %d 次 工具调用 %d 个 耗时 %dms tokens=%d/%d",
                 req.sessionId, context.model_calls, len(call_names_by_id),
                 int((time.perf_counter() - started) * 1000),
                 context.input_tokens, context.output_tokens)

    except asyncio.CancelledError:
        # 客户端断开：交由 LangGraph 取消本次 run，日志留痕便于排查「为什么这次对话没落库」
        log.info("客户端断开，取消对话 session=%s", req.sessionId)
        raise
    except TimeoutError:
        log.warning("对话超时 session=%s 预算 %.0fs", req.sessionId, settings.request_timeout)
        yield ChatEvent(type="error", message="这次回答耗时过长已中断，请把问题拆小一点再问一次。")
    except Exception as e:
        log.exception("对话处理失败 session=%s", req.sessionId)
        yield ChatEvent(type="error", message=f"AI 助手暂时不可用：{e}")


def _safe_json(raw: str) -> dict | None:
    try:
        parsed = json.loads(raw) if raw.strip() else {}
        return parsed if isinstance(parsed, dict) else None
    except json.JSONDecodeError:
        # 参数还在分片到达中，解析失败属正常，前端此时也不需要完整参数
        return None


def _looks_failed(raw: str) -> bool:
    """工具失败时返回的是中文失败文案（见 tools/_common.py），据此判断成败。"""
    return "失败" in raw[:60] or "暂时不可用" in raw[:60]


# 模型「声称草稿已生成」的措辞，以及明确否认生成的措辞。
# 用关键词而不是语义判断是启发式：宁可漏判（少补一句更正），也不要在模型只是
# 询问「要不要帮你生成草稿？」时误报。末尾是问句的一律跳过。
_DRAFT_CLAIM = ("草稿已生成", "草稿已经", "已生成草稿", "预约草稿", "预约卡片", "确认预约")
_DRAFT_DENY = ("无法生成", "不能生成", "没有生成", "没能生成", "未生成", "生成失败")


def _claims_draft(answer: str) -> bool:
    """回答里是否在宣告「预约草稿已生成」。用于兜住「没调工具却报成功」的幻觉。"""
    text = answer.strip()
    if not text or text.endswith(("？", "?")):
        return False
    if any(neg in text for neg in _DRAFT_DENY):
        return False
    return any(claim in text for claim in _DRAFT_CLAIM)


def _summarize(raw: str, limit: int = 120) -> str:
    """把工具结果压成一行摘要给前端展示（完整内容留给模型，不给前端）。"""
    if _DRAFT_MARKER in raw:
        raw = raw.split(_DRAFT_MARKER, 1)[0]
    text = " ".join(raw.split())
    return text[:limit] + ("…" if len(text) > limit else "")


def _extract_draft(raw: str):
    if _DRAFT_MARKER not in raw:
        return None
    payload = raw.split(_DRAFT_MARKER, 1)[1].strip()
    try:
        return json.loads(payload)
    except json.JSONDecodeError:
        log.warning("预约草稿解析失败: %s", payload[:200])
        return None


def _fallback_answer(draft_ready: bool, ran_tools: bool) -> str:
    """模型没写出最终回答时的兜底文案。

    常见成因是工具轮数用满、中间件直接收尾，模型没机会说话。这时如果工具其实已经查到了东西，
    就不能说「抱歉没能生成回答」——用户看到的是一堆工具调用成功却被告知失败，体验很割裂。
    按「拿到了什么」分档给话，把已经拿到的结果告诉用户。
    """
    if draft_ready:
        return ("预约草稿已经生成好了，请核对上面的门店、项目和到店时间，"
                "确认无误后点击卡片上的「确认预约」即可下单。想改时间或换项目直接告诉我。")
    if ran_tools:
        return ("我查到了一些信息（见上方的查询过程），但这次没能整理成完整回答。"
                "你可以把问题问得更具体一点，比如指明门店名或项目名，我再答一次。")
    return "抱歉，这次没能生成有效回答，请换个说法再问一次。"


async def chat_sync(req: ChatRequest) -> dict:
    """非流式版本：跑完整流程后一次性返回，供调试、后台与单测使用。"""
    events: list[ChatEvent] = []
    async for event in stream_chat(req):
        events.append(event)
    final = next((e for e in events if e.type == "done"), None)
    error = next((e for e in events if e.type == "error"), None)
    return {
        "answer": final.content if final else None,
        "error": error.message if error else None,
        "latencyMs": final.latencyMs if final else None,
        "inputTokens": final.inputTokens if final else None,
        "outputTokens": final.outputTokens if final else None,
        "tools": [
            {"name": e.toolName, "label": e.toolLabel, "ok": e.toolOk, "summary": e.toolSummary}
            for e in events if e.type == "tool_end"
        ],
        "draft": next((e.draft for e in events if e.type == "draft"), None),
    }

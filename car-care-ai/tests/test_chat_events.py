"""SSE 事件归一化测试。

这是整个服务里最容易出错的一段：要把 LangGraph 的 (message_chunk, metadata) 流翻译成
前端能懂的事件。开发过程中在这里踩过两个真 bug，所以用「脚本化的假 Agent」把它们钉住：

1. 过渡语泄漏 —— 模型调工具前说的「我这就去查」混进了最终回答。
   根因是判断「本轮是否已调工具」的标志位置位后不复位。
2. 多轮时 reset 完全不触发 —— 根因是工具调用分片按 index 累积，
   而每轮的 index 都从 0 重新开始，第二轮的调用撞上了第一轮的残留。

两条用例都会失败在修复前的实现上，所以它们是真正的回归保护，不是摆设。
"""

from __future__ import annotations

import asyncio
import json

from langchain_core.messages import AIMessageChunk, ToolMessage

from app.schemas import ChatRequest
from app.services import chat as chat_service


class FakeAgent:
    """按预设脚本吐 (chunk, metadata) 的假 Agent，替掉真实模型调用。"""

    def __init__(self, script):
        self._script = script

    async def _gen(self):
        for item in self._script:
            yield item

    def astream(self, *_args, **_kwargs):
        return self._gen()


def run_chat(script, message="帮我约西湖文一店的小保养"):
    """跑一次对话，返回事件列表。"""
    chat_service.get_agent = lambda: FakeAgent(script)
    req = ChatRequest(sessionId="test-session", message=message)

    async def collect():
        return [event async for event in chat_service.stream_chat(req)]

    return asyncio.run(collect())


def ai_text(text, tool_calls=None):
    chunk = AIMessageChunk(content=text)
    if tool_calls:
        chunk.tool_call_chunks = tool_calls
    return chunk


def tool_call(name, call_id, args="{}", index=0):
    return {"name": name, "id": call_id, "args": args, "index": index, "type": "tool_call_chunk"}


def tool_result(name, call_id, content):
    return ToolMessage(content=content, tool_call_id=call_id, name=name)


def types(events):
    return [e.type for e in events]


# ---------------------------------------------------------------------------
# 基本契约
# ---------------------------------------------------------------------------


def test_plain_answer_has_no_tool_events():
    events = run_chat([(ai_text("你的车还有 26 天到保养期。"), {"langgraph_node": "model"})])
    assert types(events) == ["start", "delta", "done"]
    assert events[-1].content == "你的车还有 26 天到保养期。"


def test_deltas_in_one_round_are_joined_into_the_answer():
    """同一轮的多片正文要拼成完整回答；跨轮的过渡语则被 reset 掉（见后面的回归用例）。"""
    events = run_chat([
        (ai_text("前半"), {"langgraph_node": "model"}),
        (ai_text("后半"), {"langgraph_node": "model"}),
    ])
    done = events[-1]
    assert done.type == "done"
    assert done.content == "前半后半"


def test_tool_start_and_end_are_reported():
    events = run_chat([
        (ai_text("", [tool_call("search_stores", "call_a")]), {"langgraph_node": "model"}),
        (tool_result("search_stores", "call_a", "查询到以下营业门店：- 门店ID=1"), {"langgraph_node": "tools"}),
        (ai_text("杭州有 2 家门店。"), {"langgraph_node": "model"}),
    ])
    assert types(events) == ["start", "tool_start", "tool_end", "delta", "done"]

    start_event = next(e for e in events if e.type == "tool_start")
    assert start_event.toolName == "search_stores"
    assert start_event.toolLabel == "正在查询营业门店"

    end_event = next(e for e in events if e.type == "tool_end")
    assert end_event.toolOk is True
    assert "营业门店" in end_event.toolSummary
    assert events[-1].content == "杭州有 2 家门店。"


def test_tool_failure_is_flagged():
    events = run_chat([
        (ai_text("", [tool_call("search_stores", "call_a")]), {"langgraph_node": "model"}),
        (tool_result("search_stores", "call_a",
                     "门店查询失败：车管家数据服务暂时不可用。请如实告诉用户……"), {"langgraph_node": "tools"}),
        (ai_text("门店信息暂时查不到，稍后再试。"), {"langgraph_node": "model"}),
    ])
    assert next(e for e in events if e.type == "tool_end").toolOk is False


# ---------------------------------------------------------------------------
# 回归 1：过渡语必须被 reset 掉（含多轮场景）
# ---------------------------------------------------------------------------


def test_narration_before_tool_call_is_reset():
    events = run_chat([
        (ai_text("Let me look that up for you."), {"langgraph_node": "model"}),
        (ai_text("", [tool_call("search_stores", "call_a")]), {"langgraph_node": "model"}),
        (tool_result("search_stores", "call_a", "查询到以下营业门店：- 门店ID=1"), {"langgraph_node": "tools"}),
        (ai_text("杭州目前有 2 家门店。"), {"langgraph_node": "model"}),
    ])
    assert "reset" in types(events)
    assert events[-1].content == "杭州目前有 2 家门店。"
    assert "Let me" not in events[-1].content


def test_narration_is_reset_on_every_tool_round():
    """回归用例：第二轮及之后的过渡语也必须被清掉。

    修复前的实现用「本轮是否已调工具」的布尔标志 + 按 index 累积的 pending_calls，
    导致第二轮开始 reset 完全不触发，最终回答里会堆满各轮的英文自述。
    """
    events = run_chat([
        # 第 1 轮：先说一句，再调工具
        (ai_text("I found the store."), {"langgraph_node": "model"}),
        (ai_text("", [tool_call("search_stores", "call_a")]), {"langgraph_node": "model"}),
        (tool_result("search_stores", "call_a", "查询到以下营业门店：- 门店ID=1"), {"langgraph_node": "tools"}),
        # 第 2 轮：index 又从 0 开始，且 id 不同 —— 这里就是踩坑点
        (ai_text("Let me check the items."), {"langgraph_node": "model"}),
        (ai_text("", [tool_call("search_service_items", "call_b")]), {"langgraph_node": "model"}),
        (tool_result("search_service_items", "call_b", "查询到以下在售项目：- 项目ID=1"), {"langgraph_node": "tools"}),
        # 第 3 轮：最终回答
        (ai_text("小保养 268 元，含机油机滤。"), {"langgraph_node": "model"}),
    ])
    resets = types(events).count("reset")
    assert resets == 2, f"两轮工具调用各应触发一次 reset，实际 {resets}"

    done = events[-1]
    assert done.type == "done"
    assert done.content == "小保养 268 元，含机油机滤。"
    for leak in ("I found", "Let me"):
        assert leak not in done.content


def test_two_tool_calls_in_one_round_both_reported():
    events = run_chat([
        (ai_text("", [
            tool_call("search_stores", "call_a", index=0),
            tool_call("get_my_vehicles", "call_b", index=1),
        ]), {"langgraph_node": "model"}),
        (tool_result("search_stores", "call_a", "查询到以下营业门店：- 门店ID=1"), {"langgraph_node": "tools"}),
        (tool_result("get_my_vehicles", "call_b", "该用户的车辆档案：- 车辆ID=1"), {"langgraph_node": "tools"}),
        (ai_text("门店和车辆都查到了。"), {"langgraph_node": "model"}),
    ])
    assert types(events).count("tool_start") == 2
    assert types(events).count("tool_end") == 2
    assert events[-1].content == "门店和车辆都查到了。"


# ---------------------------------------------------------------------------
# 预约草稿
# ---------------------------------------------------------------------------


def draft_payload():
    return {
        "storeId": 1,
        "storeName": "车管家·西湖文一店",
        "itemIds": [1],
        "itemNames": ["小保养（更换机油机滤）"],
        "appointmentTime": "2026-09-26 09:00",
        "estimatedAmount": 268.0,
    }


def test_draft_event_is_extracted_and_not_leaked_into_summary():
    payload = draft_payload()
    raw = ("草稿已生成：车管家·西湖文一店｜小保养（更换机油机滤）｜预估 ¥268.00\n"
           f"__DRAFT__{json.dumps(payload, ensure_ascii=False)}")
    events = run_chat([
        (ai_text("", [tool_call("build_booking_draft", "call_d")]), {"langgraph_node": "model"}),
        (tool_result("build_booking_draft", "call_d", raw), {"langgraph_node": "tools"}),
        (ai_text("草稿已生成，请确认。"), {"langgraph_node": "model"}),
    ])
    drafts = [e for e in events if e.type == "draft"]
    assert len(drafts) == 1
    # ChatEvent.draft 声明的是 BookingDraft，pydantic 会把工具吐出的 dict 转成模型对象
    assert drafts[0].draft.storeId == 1
    assert drafts[0].draft.estimatedAmount == 268.0

    end_event = next(e for e in events if e.type == "tool_end")
    assert "__DRAFT__" not in end_event.toolSummary, "草稿标记不该出现在给用户看的摘要里"


def test_broken_draft_payload_does_not_break_the_stream():
    events = run_chat([
        (ai_text("", [tool_call("build_booking_draft", "call_d")]), {"langgraph_node": "model"}),
        (tool_result("build_booking_draft", "call_d", "草稿已生成\n__DRAFT__{不是合法 json"), {"langgraph_node": "tools"}),
        (ai_text("草稿已生成。"), {"langgraph_node": "model"}),
    ])
    assert not [e for e in events if e.type == "draft"]
    assert events[-1].type == "done"


# ---------------------------------------------------------------------------
# 兜底与异常
# ---------------------------------------------------------------------------


def test_fallback_when_model_produces_no_text_after_draft():
    """工具轮数用满时模型可能没机会说话，此时不能甩「抱歉没能生成回答」。"""
    payload = draft_payload()
    events = run_chat([
        (ai_text("", [tool_call("build_booking_draft", "call_d")]), {"langgraph_node": "model"}),
        (tool_result("build_booking_draft", "call_d",
                     f"草稿已生成\n__DRAFT__{json.dumps(payload, ensure_ascii=False)}"), {"langgraph_node": "tools"}),
        (ai_text(""), {"langgraph_node": "model"}),
    ])
    done = events[-1]
    assert done.type == "done"
    assert "确认预约" in done.content
    assert "抱歉" not in done.content


def test_fallback_when_tools_ran_but_no_answer():
    events = run_chat([
        (ai_text("", [tool_call("search_stores", "call_a")]), {"langgraph_node": "model"}),
        (tool_result("search_stores", "call_a", "查询到以下营业门店：- 门店ID=1"), {"langgraph_node": "tools"}),
        (ai_text(""), {"langgraph_node": "model"}),
    ])
    assert events[-1].type == "done"
    assert "没能整理成完整回答" in events[-1].content


def test_agent_failure_becomes_error_event():
    class BoomAgent:
        async def _gen(self):
            raise RuntimeError("上游模型 500")
            yield  # pragma: no cover - 让它是异步生成器

        def astream(self, *_a, **_k):
            return self._gen()

    chat_service.get_agent = lambda: BoomAgent()
    req = ChatRequest(sessionId="s", message="你好")

    async def collect():
        return [e async for e in chat_service.stream_chat(req)]

    events = asyncio.run(collect())
    assert events[0].type == "start"
    assert events[-1].type == "error"
    assert "上游模型 500" in events[-1].message

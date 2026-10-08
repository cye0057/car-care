"""工具层测试：验证「失败要如实说、不编造」这条约束真的落实在代码里。

不联网：Java 客户端用假对象替换。重点覆盖预约草稿的入参校验——
它是唯一会产生「看起来能下单」的产物的工具，一旦放行不属于该门店的项目，
用户点确认后会在下单接口报错，属于必须拦在前面的错误。
"""

from __future__ import annotations

import asyncio

from langgraph.prebuilt import ToolRuntime

from app.agent.context import AgentContext
from app.agent.tools import ALL_TOOLS
from app.agent.tools.booking import build_booking_draft
from app.clients import carcare
from app.clients.carcare import ToolResult
from app.schemas import CouponBrief, UserContext

ITEM_1 = {"id": 1, "storeId": 1, "name": "小保养（更换机油机滤）", "price": 268.0, "status": 1}
ITEM_10 = {"id": 10, "storeId": 2, "name": "大保养（机油三滤+全车检查）", "price": 869.0, "status": 1}


class FakeClient:
    def __init__(self, items=None, stores=None, ok=True):
        self._items = items or [ITEM_1]
        self._stores = stores or [{"id": 1, "name": "车管家·西湖文一店", "city": "杭州", "address": "杭州市西湖区文一西路 128 号"}]
        self._ok = ok

    async def search_items(self, store_id=None, keyword=None, limit=10):
        if not self._ok:
            return ToolResult.failure("车管家数据服务暂时不可用")
        return ToolResult.success([i for i in self._items if store_id is None or i["storeId"] == store_id])

    async def search_stores(self, city=None, keyword=None, limit=5):
        if not self._ok:
            return ToolResult.failure("车管家数据服务暂时不可用")
        return ToolResult.success(self._stores)


def install_fake(monkeypatch, client):
    monkeypatch.setattr(carcare, "_client", client)


def make_context(coupons=None):
    return AgentContext(
        session_id="s1",
        user_id=2,
        user_context=UserContext(userId=2, name="张三", coupons=coupons or []),
    )


def call_draft(context, **kwargs):
    args = {"store_id": 1, "item_ids": [1]}
    args.update(kwargs)
    return asyncio.run(build_booking_draft.ainvoke({"runtime": make_runtime(context), **args}))


def make_runtime(context):
    """真实的 ToolRuntime 实例。

    不能自己造一个鸭子类型的假对象——langchain 会用 pydantic 校验这个参数，
    只认 ToolRuntime 本身。所以老老实实按它的字段构造一个。
    """
    return ToolRuntime(
        state={},
        context=context,
        config={},
        stream_writer=lambda *_a, **_k: None,
        tool_call_id=None,
        store=None,
    )


# ---------------------------------------------------------------------------


def test_tool_set_is_registered_and_named():
    names = {t.name for t in ALL_TOOLS}
    assert names == {
        "search_stores", "search_service_items", "get_packages", "list_claimable_coupons",
        "get_my_vehicles", "get_my_coupons", "get_my_orders", "get_order_progress",
        "suggest_maintenance_plan", "search_maintenance_knowledge", "build_booking_draft",
    }
    # @tool 要求有 docstring 或 description，否则模型看不到何时该调它
    for tool in ALL_TOOLS:
        assert (tool.description or "").strip(), f"{tool.name} 缺少描述"


def test_draft_contains_structured_payload(monkeypatch):
    install_fake(monkeypatch, FakeClient())
    raw = call_draft(make_context(), appointment_time="2026-09-26 09:00")
    assert "__DRAFT__" in raw
    assert "车管家·西湖文一店" in raw
    assert "¥268.00" in raw


def test_draft_rejects_item_from_another_store(monkeypatch):
    """项目 10 属于门店 2，却拿来给门店 1 下单 —— 必须拒绝而不是静默放行。"""
    install_fake(monkeypatch, FakeClient())
    raw = call_draft(make_context(), store_id=1, item_ids=[10])
    assert "生成草稿失败" in raw
    assert "__DRAFT__" not in raw
    assert "不要凭印象编造项目" in raw


def test_draft_reports_upstream_failure_instead_of_faking(monkeypatch):
    install_fake(monkeypatch, FakeClient(ok=False))
    raw = call_draft(make_context())
    assert "生成草稿失败" in raw
    assert "__DRAFT__" not in raw


def test_draft_rejects_empty_item_ids(monkeypatch):
    install_fake(monkeypatch, FakeClient())
    raw = call_draft(make_context(), item_ids=[])
    assert "生成草稿失败" in raw


def test_draft_mentions_best_applicable_coupon(monkeypatch):
    install_fake(monkeypatch, FakeClient())
    coupons = [
        CouponBrief(couponId=7, storeId=1, title="满 200 减 50",
                    minPrice=200.0, discountPrice=50.0),
        # 属于另一家店、折扣更大，但不该被选中
        CouponBrief(couponId=8, storeId=2, title="另一家店的券",
                    minPrice=0.0, discountPrice=99.0),
    ]
    raw = call_draft(make_context(coupons=coupons))
    assert "满 200 减 50" in raw
    assert "另一家店的券" not in raw, "不适用该门店的券不该出现在草稿提示里"


def test_coupon_skipped_when_below_threshold(monkeypatch):
    install_fake(monkeypatch, FakeClient())
    coupons = [CouponBrief(couponId=1, storeId=1, title="满 1000 减 100",
                           minPrice=1000.0, discountPrice=100.0)]
    raw = call_draft(make_context(coupons=coupons))
    assert "满 1000 减 100" not in raw, "268 元的单不该提示满 1000 才能用的券"


def test_store_wide_coupon_applies_to_any_store(monkeypatch):
    """storeId 为空的券是全平台通用的，任何门店都该提示可用。"""
    install_fake(monkeypatch, FakeClient())
    coupons = [CouponBrief(couponId=9, storeId=None, title="全平台 30 元代金券",
                           minPrice=0.0, cashPrice=30.0)]
    raw = call_draft(make_context(coupons=coupons))
    assert "全平台 30 元代金券" in raw

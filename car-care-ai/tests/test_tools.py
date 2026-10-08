"""工具层测试：验证「失败要如实说、不编造」这条约束真的落实在代码里。

不联网：Java 客户端用假对象替换。重点覆盖预约草稿的入参校验——
它是唯一会产生「看起来能下单」的产物的工具，一旦放行不属于该门店的项目，
用户点确认后会在下单接口报错，属于必须拦在前面的错误。
"""

from __future__ import annotations

import asyncio
import json

from langgraph.prebuilt import ToolRuntime

from app.agent.context import AgentContext
from app.agent.tools import ALL_TOOLS
from app.agent.tools.booking import build_booking_draft
from app.clients import carcare
from app.clients.carcare import ToolResult
from app.schemas import CouponBrief, UserContext

ITEM_1 = {"id": 1, "storeId": 1, "name": "小保养（更换机油机滤）", "price": 268.0, "status": 1}
ITEM_6 = {"id": 6, "storeId": 1, "name": "前刹车片更换（一对）", "price": 560.0, "status": 1}
ITEM_10 = {"id": 10, "storeId": 2, "name": "大保养（机油三滤+全车检查）", "price": 869.0, "status": 1}

STORE_1 = {"id": 1, "name": "车管家·西湖文一店", "city": "杭州", "address": "杭州市西湖区文一西路 128 号"}
STORE_2 = {"id": 2, "name": "车管家·滨江江南大道店", "city": "杭州", "address": "杭州市滨江区江南大道 450 号"}

PKG_1 = {"id": 1, "storeId": 1, "name": "安心小保养套餐", "price": 668.0,
         "itemNames": ["小保养（更换机油机滤）", "空调滤芯更换"]}
PKG_2 = {"id": 2, "storeId": 2, "name": "大保养尊享套餐", "price": 1599.0, "itemNames": []}


class FakeClient:
    """假的 Java 内部接口。

    search_items 的 ids 分支刻意与 Java 侧行为一致：按 id 点查时**不**再按 storeId 过滤，
    否则「项目属于别家店」会被吞成「项目不存在」，草稿的报错就会误导模型。
    """

    def __init__(self, items=None, stores=None, packages=None, ok=True):
        self._items = items if items is not None else [ITEM_1, ITEM_6, ITEM_10]
        self._stores = stores if stores is not None else [STORE_1, STORE_2]
        self._packages = packages if packages is not None else [PKG_1, PKG_2]
        self._ok = ok

    async def search_items(self, store_id=None, keyword=None, ids=None, limit=10):
        if not self._ok:
            return ToolResult.failure("车管家数据服务暂时不可用")
        if ids:
            return ToolResult.success([i for i in self._items if i["id"] in ids])
        if store_id is not None:
            return ToolResult.success([i for i in self._items if i["storeId"] == store_id])
        return ToolResult.success(self._items)

    async def search_stores(self, city=None, keyword=None, store_id=None, limit=5):
        if not self._ok:
            return ToolResult.failure("车管家数据服务暂时不可用")
        if store_id is not None:
            return ToolResult.success([s for s in self._stores if s["id"] == store_id])
        return ToolResult.success(self._stores)

    async def list_packages(self, store_id, limit=5):
        if not self._ok:
            return ToolResult.failure("车管家数据服务暂时不可用")
        return ToolResult.success([p for p in self._packages if p["storeId"] == store_id])


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
    """项目 10 属于门店 2，却拿来给门店 1 下单 —— 必须拒绝而不是静默放行。

    这条也钉住「不要擅自替换项目」：报错要明确告诉模型别换一个项目顶上，
    否则模型会从自己看得见的项目里挑一个生成草稿（实测出现过用户要前刹车、草稿却是小保养）。
    """
    install_fake(monkeypatch, FakeClient())
    raw = call_draft(make_context(), store_id=1, item_ids=[10])
    assert "生成草稿失败" in raw
    assert "__DRAFT__" not in raw
    assert "属于门店 2" in raw, "报错要指明项目实际属于哪家店，模型才知道该说什么"
    assert "不要擅自把项目替换成别的" in raw


def test_draft_distinguishes_missing_item_from_wrong_store(monkeypatch):
    """「项目查不到」和「项目属于别家店」是两种错误，提示必须分开。

    合并成一句「不属于该门店或在售状态已变更」时，模型无法判断该换项目还是换门店。
    """
    install_fake(monkeypatch, FakeClient())
    raw = call_draft(make_context(), store_id=1, item_ids=[999])
    assert "生成草稿失败" in raw
    assert "999" in raw and "查不到" in raw
    assert "属于门店" not in raw, "项目根本不存在，不该报成「属于别家店」"


def test_draft_rejects_unknown_store(monkeypatch):
    """门店查不到（不存在或未营业）时不能生成草稿，否则会推给用户一张下不了单的卡片。"""
    install_fake(monkeypatch, FakeClient())
    raw = call_draft(make_context(), store_id=99, item_ids=[1])
    assert "生成草稿失败" in raw
    assert "__DRAFT__" not in raw


def test_draft_supports_package(monkeypatch):
    """套餐单：itemNames 要放套餐名，否则卡片上「项目」那一行是空的。"""
    install_fake(monkeypatch, FakeClient())
    raw = call_draft(make_context(), store_id=1, item_ids=[], package_id=1)
    assert "__DRAFT__" in raw
    assert "安心小保养套餐" in raw
    payload = json.loads(raw.split("__DRAFT__", 1)[1])
    assert payload["packageId"] == 1
    assert payload["packageName"] == "安心小保养套餐"
    assert payload["itemIds"] == []
    assert payload["itemNames"] == ["安心小保养套餐"]
    assert payload["estimatedAmount"] == 668.0


def test_draft_rejects_package_from_another_store(monkeypatch):
    install_fake(monkeypatch, FakeClient())
    raw = call_draft(make_context(), store_id=1, item_ids=[], package_id=2)
    assert "生成草稿失败" in raw
    assert "__DRAFT__" not in raw
    assert "不要凭印象编造套餐" in raw


def test_draft_rejects_package_and_items_together(monkeypatch):
    """套餐与单项不能开在同一张订单里：同时传时前端只能下单一个，另一个会静默消失。"""
    install_fake(monkeypatch, FakeClient())
    raw = call_draft(make_context(), store_id=1, item_ids=[1], package_id=1)
    assert "生成草稿失败" in raw
    assert "__DRAFT__" not in raw
    assert "二选一" in raw


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

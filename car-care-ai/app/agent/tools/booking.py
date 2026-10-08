"""预约草稿工具。

刻意**不**做真正的下单：下单涉及订单状态机、延迟关单队列、优惠券核销、库存扣减，
这些逻辑已经在 Java 侧沉淀好了。让 AI 再实现一遍等于把核心链路复制成两份，
出问题时两边的状态还可能不一致。

所以这里只产出「草稿」——把门店、项目、金额、时间整理成结构化数据推给前端，
用户在卡片上点确认，前端再调既有的 POST /api/orders 走正常下单流程。
"""

from __future__ import annotations

import json

from langchain.tools import ToolRuntime, tool

from app.agent.context import AgentContext
from app.agent.tools._common import money
from app.clients.carcare import get_client
from app.schemas import BookingDraft


@tool
async def build_booking_draft(
    runtime: ToolRuntime[AgentContext],
    store_id: int,
    item_ids: list[int],
    appointment_time: str | None = None,
    vehicle_id: int | None = None,
    note: str | None = None,
) -> str:
    """生成预约草稿卡片。用户已明确要预约、且已确定门店与项目时调用。

    这只是草稿，不会真的下单——用户需要在前端卡片上点确认才会创建订单。
    生成后请告诉用户：确认门店、项目和预估金额，点击卡片上的「确认预约」即可下单。

    Args:
        store_id: 门店 ID，必填。
        item_ids: 要预约的项目 ID 列表，必填。必须来自 search_service_items 的查询结果。
        appointment_time: 用户希望的到店时间，格式 yyyy-MM-dd HH:mm。用户没提就留空。
        vehicle_id: 车辆 ID。用户有多台车且已指明时传入。
        note: 备注，例如用户提到的特殊要求。
    """
    if not item_ids:
        return "生成草稿失败：item_ids 不能为空。请先用 search_service_items 查到具体项目再调用。"

    items_result = await get_client().search_items(store_id=store_id)
    if not items_result.ok:
        return f"生成草稿失败：{items_result.error}。请如实告知用户暂时无法生成预约草稿。"

    by_id = {int(it["id"]): it for it in items_result.data if it.get("id") is not None}
    picked = [by_id[i] for i in item_ids if i in by_id]
    missing = [i for i in item_ids if i not in by_id]
    if missing:
        return (f"生成草稿失败：项目 {missing} 不属于门店 {store_id} 或在售状态已变更。"
                f"请重新用 search_service_items 确认该门店的在售项目，不要凭印象编造项目。")

    store_result = await get_client().search_stores()
    store_name = next(
        (str(s["name"]) for s in store_result.data if int(s.get("id", -1)) == store_id),
        f"门店 {store_id}",
    )

    amount = sum(float(it.get("price") or 0) for it in picked)
    draft = BookingDraft(
        storeId=store_id,
        storeName=store_name,
        itemIds=[int(it["id"]) for it in picked],
        itemNames=[str(it.get("name")) for it in picked],
        vehicleId=vehicle_id,
        appointmentTime=appointment_time,
        estimatedAmount=round(amount, 2),
        note=note,
    )

    # 顺带算出可用券能省多少：数据来自 Java 下发的用户券，是真实数据而非估算
    coupon_hint = _best_coupon_hint(runtime, store_id, amount)
    payload = draft.model_dump()
    if coupon_hint:
        payload["note"] = f"{note}｜{coupon_hint}" if note else coupon_hint

    summary = (f"草稿已生成：{store_name}｜{'、'.join(draft.itemNames)}"
               f"｜预估 {money(draft.estimatedAmount)}"
               + (f"｜{appointment_time}" if appointment_time else "")
               + (f"｜{coupon_hint}" if coupon_hint else ""))
    # 结构化草稿单独放一行，chat 服务会解析出来转成 draft 事件推给前端
    return f"{summary}\n__DRAFT__{json.dumps(payload, ensure_ascii=False)}"


def _best_coupon_hint(runtime: ToolRuntime[AgentContext], store_id: int, amount: float) -> str | None:
    ctx = runtime.context.user_context if runtime.context else None
    if ctx is None or not ctx.coupons:
        return None
    best: tuple[float, str] | None = None
    for c in ctx.coupons:
        if c.storeId is not None and c.storeId != store_id:
            continue
        if c.minPrice is not None and amount < float(c.minPrice):
            continue
        discount = float(c.discountPrice or c.cashPrice or 0)
        if discount <= 0:
            continue
        if best is None or discount > best[0]:
            best = (discount, c.title)
    if best is None:
        return None
    return f"可用「{best[1]}」预计再减 ¥{best[0]:.2f}（最终以订单结算为准）"

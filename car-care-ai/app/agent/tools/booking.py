"""预约草稿工具。

刻意**不**做真正的下单：下单涉及订单状态机、延迟关单队列、优惠券核销、库存扣减，
这些逻辑已经在 Java 侧沉淀好了。让 AI 再实现一遍等于把核心链路复制成两份，
出问题时两边的状态还可能不一致。

所以这里只产出「草稿」——把门店、项目/套餐、金额、时间整理成结构化数据推给前端，
用户在卡片上点确认，前端再调既有的 POST /api/orders 走正常下单流程。
"""

from __future__ import annotations

import json

from langchain.tools import ToolRuntime, tool

from app.agent.context import AgentContext
from app.agent.tools._common import money
from app.clients.carcare import get_client
from app.schemas import BookingDraft

# 一次草稿允许的项目数上限，与 Java 侧点查接口的 id 上限（50）对齐
MAX_ITEMS = 50


@tool
async def build_booking_draft(
    runtime: ToolRuntime[AgentContext],
    store_id: int,
    item_ids: list[int] | None = None,
    package_id: int | None = None,
    appointment_time: str | None = None,
    vehicle_id: int | None = None,
    note: str | None = None,
) -> str:
    """生成预约草稿卡片。用户已明确要预约、且已确定门店与项目（或套餐）时调用。

    这只是草稿，不会真的下单——用户需要在前端卡片上点确认才会创建订单。
    生成后请告诉用户：确认门店、项目和预估金额，点击卡片上的「确认预约」即可下单。

    Args:
        store_id: 门店 ID，必填。
        item_ids: 要预约的单项服务 ID 列表，必须来自 search_service_items 的查询结果。
            用户要的是整包套餐时改用 package_id，两者给一个即可（不能同时给）。
        package_id: 套餐 ID，必须来自 get_packages 的查询结果。
        appointment_time: 用户希望的到店时间，格式 yyyy-MM-dd HH:mm。用户没提就留空。
        vehicle_id: 车辆 ID。用户有多台车且已指明时传入。
        note: 备注，例如用户提到的特殊要求。
    """
    item_ids = item_ids or []

    # --- 入参自检：把「该不该调这个工具」的判断留在这里，而不是让模型自己圆 ---
    if not item_ids and package_id is None:
        return ("生成草稿失败：item_ids 与 package_id 至少要有一个。"
                "请先用 search_service_items 或 get_packages 查到具体项目/套餐，再调用本工具。")
    if item_ids and package_id is not None:
        # 平台一张订单只含一个套餐或一个单项（见 OrderCreateDTO 的「二选一」），
        # 同时传会让前端只能下单其中一个，另一个静默消失，所以直接拒绝。
        return ("生成草稿失败：套餐与单项服务不能开在同一张订单里，平台一张订单只含一个套餐或一个项目。"
                "请让用户二选一，或分成两张草稿分别预约。")
    if len(item_ids) > MAX_ITEMS:
        return f"生成草稿失败：一次最多预约 {MAX_ITEMS} 个项目。"

    # --- 门店：按 id 点查，拿门店名；查不到说明门店不存在或未营业 ---
    store_result = await get_client().search_stores(store_id=store_id)
    if not store_result.ok:
        return f"生成草稿失败：{store_result.error}。请如实告知用户暂时无法生成预约草稿。"
    store = next((s for s in store_result.data if int(s.get("id", -1)) == store_id), None)
    if store is None:
        return (f"生成草稿失败：门店 {store_id} 不存在或当前未营业。"
                f"请用 search_stores 重新确认门店后再生成草稿。")

    picked_items: list[dict] = []
    if item_ids:
        # 按 id 点查（Java 侧不分页）。这里必须点查而不是「取该门店前 N 个项目再比对」：
        # 后者在门店项目多于 N 个时会把合法项目判成「不属于该门店」，
        # 模型被这句错误提示推着换一个它看得见的项目，最终用户要的项目就被悄悄替换了。
        items_result = await get_client().search_items(ids=item_ids)
        if not items_result.ok:
            return f"生成草稿失败：{items_result.error}。请如实告知用户暂时无法生成预约草稿。"
        by_id = {int(it["id"]): it for it in items_result.data if it.get("id") is not None}
        missing = [i for i in item_ids if i not in by_id]
        if missing:
            return (f"生成草稿失败：项目 {missing} 查不到（可能已下架）。"
                    f"请重新用 search_service_items 确认在售项目，不要凭印象编造项目。")
        wrong_store = [i for i in item_ids if int(by_id[i].get("storeId") or -1) != store_id]
        if wrong_store:
            detail = "、".join(f"{i}（属于门店 {by_id[i].get('storeId')}）" for i in wrong_store)
            return (f"生成草稿失败：项目 {detail} 不在门店 {store_id}。"
                    f"请如实告诉用户这家店没有该项目，并给出该门店确实在售的相近项目，"
                    f"由用户决定换项目还是换门店——不要擅自把项目替换成别的。")
        picked_items = [by_id[i] for i in item_ids]

    picked_pkg: dict | None = None
    if package_id is not None:
        # 校验用 limit=50：与点查等价，避免门店套餐多于默认 5 个时把合法套餐判成不存在
        pkgs = await get_client().list_packages(store_id, limit=50)
        if not pkgs.ok:
            return f"生成草稿失败：{pkgs.error}。请如实告知用户暂时无法生成预约草稿。"
        picked_pkg = next((p for p in pkgs.data if int(p.get("id", -1)) == package_id), None)
        if picked_pkg is None:
            return (f"生成草稿失败：套餐 {package_id} 不属于门店 {store_id} 或已停售。"
                    f"请重新用 get_packages 确认该门店的启用套餐，不要凭印象编造套餐。")

    amount = sum(float(it.get("price") or 0) for it in picked_items)
    if picked_pkg is not None:
        amount += float(picked_pkg.get("price") or 0)

    # 卡片上「项目」那一行渲染的是 itemNames，套餐单就把套餐名放进去，
    # 否则用户只看到金额、看不出买的是哪个套餐。
    item_names = [str(picked_pkg.get("name"))] if picked_pkg is not None \
        else [str(it.get("name")) for it in picked_items]

    draft = BookingDraft(
        storeId=store_id,
        storeName=str(store.get("name")),
        itemIds=[int(it["id"]) for it in picked_items],
        itemNames=item_names,
        packageId=int(picked_pkg["id"]) if picked_pkg is not None else None,
        packageName=str(picked_pkg.get("name")) if picked_pkg is not None else None,
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

    summary = (f"草稿已生成：{draft.storeName}｜{'、'.join(item_names)}"
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

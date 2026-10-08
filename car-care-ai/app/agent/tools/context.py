"""用户私有数据工具：车辆档案、订单、工单进度、保养建议。

这些数据**不查数据库**，而是读 Java 随请求下发的 AgentContext.userContext。
AI 服务因此完全不持有用户态：即使被人拿到内部令牌，也没有「按 userId 查别人数据」的入口。
"""

from __future__ import annotations

from datetime import date, datetime

from langchain.tools import ToolRuntime, tool

from app.agent.context import AgentContext
from app.agent.tools._common import money, text

_NO_CONTEXT = ("没有拿到该用户的车辆/订单数据（可能是未登录或后端上下文下发失败）。"
               "请告诉用户需要先登录后重试，不要编造任何车辆或订单信息。")


def _ctx(runtime: ToolRuntime[AgentContext]):
    return runtime.context.user_context if runtime.context else None


@tool
async def get_my_vehicles(runtime: ToolRuntime[AgentContext]) -> str:
    """查询当前登录车主的车辆档案（车牌、品牌车型、里程、下次保养日期）。

    用于回答「我的车」「我的车该保养了吗」，也是给出保养建议的前提。
    用户没登录时不会有数据，此时如实告知需要登录。
    """
    ctx = _ctx(runtime)
    if ctx is None or not ctx.vehicles:
        return _NO_CONTEXT if ctx is None else "该用户还没有登记车辆。可以引导他去「我的-车辆档案」添加车辆，之后就能给出针对性建议。"
    lines = ["该用户的车辆档案："]
    for v in ctx.vehicles:
        lines.append(
            f"- 车辆ID={v.id}｜{text(v.plateNumber)}｜{text(v.brand, '')}{text(v.model, '')}"
            f"｜颜色：{text(v.color)}｜里程：{v.mileage if v.mileage is not None else '未记录'}公里"
            f"｜下次保养日期：{text(v.nextMaintainDate, '未设置')}"
        )
    return "\n".join(lines)


@tool
async def get_my_coupons(runtime: ToolRuntime[AgentContext], store_id: int | None = None) -> str:
    """查询当前车主**已经领取**、还没用且没过期的优惠券。

    用于回答「我有什么券」「有没有优惠」「哪张券能用在这家店」。
    注意区分：平台正在发放、还没领的券要用 list_claimable_coupons 查。

    Args:
        store_id: 门店 ID。想知道某家店能用的券就带上，想查全部已领券则留空。
    """
    ctx = _ctx(runtime)
    if ctx is None:
        return _NO_CONTEXT
    coupons = ctx.coupons
    if store_id is not None:
        # storeId 为空表示全平台通用券，也要算进「这家店能用」
        coupons = [c for c in coupons if c.storeId is None or c.storeId == store_id]
    if not coupons:
        return ("该用户当前没有已领取且在有效期内的优惠券。"
                "不要虚构优惠金额；可以顺带告诉他平台上还有哪些券可以领（用 list_claimable_coupons 查）。")
    lines = ["该用户已领取且可用的优惠券："]
    for c in coupons:
        lines.append(
            f"- 券ID={c.couponId}｜{text(c.title)}"
            f"｜类型：{text(c.typeDesc, '优惠券')}"
            f"｜满：{money(c.minPrice)}｜减：{money(c.discountPrice or c.cashPrice)}"
            f"｜有效期至：{text(c.validEndTime)}"
            f"｜适用门店ID：{text(c.storeId, '全部门店')}"
        )
    return "\n".join(lines)


@tool
async def get_my_orders(runtime: ToolRuntime[AgentContext]) -> str:
    """查询当前车主的近期订单（含状态、金额、预约时间、下单项目）。

    用于回答「我有哪些订单」「我上次做的什么项目」。
    """
    ctx = _ctx(runtime)
    if ctx is None or not ctx.recentOrders:
        return _NO_CONTEXT if ctx is None else "该用户还没有订单记录。"
    lines = ["该用户的近期订单："]
    for o in ctx.recentOrders:
        items = "、".join(i.itemName for i in o.items) if o.items else "明细未记录"
        lines.append(
            f"- 订单号 {o.orderNo}｜门店：{text(o.storeName)}｜状态：{text(o.statusText)}"
            f"｜实付：{money(o.actualAmount)}｜预约时间：{text(o.appointmentTime)}"
            f"｜项目：{items}"
        )
    return "\n".join(lines)


@tool
async def get_order_progress(runtime: ToolRuntime[AgentContext], order_no: str | None = None) -> str:
    """查询施工工单的进度（待接单/维修中/待验收/已完工、技师、进度描述、预计完工时间）。

    用于回答「我的车修到哪了」「什么时候能提车」。
    订单号可以从 get_my_orders 的结果里拿到。

    Args:
        order_no: 订单号。用户特指某个订单时传入；留空则返回全部在施工工单的进度。
    """
    ctx = _ctx(runtime)
    if ctx is None:
        return _NO_CONTEXT
    work_orders = ctx.workOrders
    if order_no:
        work_orders = [w for w in work_orders if w.orderNo == order_no]
        if not work_orders:
            return (f"没有查到订单 {order_no} 的工单进度。"
                    f"可能是该订单还没进入施工阶段，或订单号不属于当前用户。请如实告知，不要猜测进度。")
    if not work_orders:
        return "该用户当前没有进行中的工单（订单可能还未支付，或已完工归档）。"
    lines = ["工单进度："]
    for w in work_orders:
        lines.append(
            f"- 订单号 {w.orderNo}｜状态：{text(w.statusText)}｜技师：{text(w.technician)}"
            f"｜进度：{text(w.progressDesc)}｜开始：{text(w.startTime)}｜完工：{text(w.finishTime)}"
        )
    return "\n".join(lines)


@tool
async def suggest_maintenance_plan(runtime: ToolRuntime[AgentContext], vehicle_id: int | None = None) -> str:
    """基于车辆档案计算保养提醒（下次保养日期是否临近/已超期、里程推算）。

    用于回答「我的车该保养了吗」「什么时候该保养」。
    这只是把已知的档案数据整理成判断依据，具体的保养项目建议要再结合
    search_maintenance_knowledge 检索到的周期标准。

    Args:
        vehicle_id: 车辆 ID。用户有多台车且指明了某一台时传入，否则留空返回全部车辆。
    """
    ctx = _ctx(runtime)
    if ctx is None or not ctx.vehicles:
        return _NO_CONTEXT if ctx is None else "该用户还没有登记车辆，无法给出保养提醒。"
    vehicles = ctx.vehicles
    if vehicle_id is not None:
        vehicles = [v for v in vehicles if v.id == vehicle_id]
        if not vehicles:
            return f"没有找到车辆ID={vehicle_id}，可能不属于当前用户。请让用户确认车辆。"

    today = date.today()
    lines = [f"今天日期：{today.isoformat()}。保养提醒依据："]
    for v in vehicles:
        label = f"{text(v.plateNumber)}（{text(v.brand, '')}{text(v.model, '')}）"
        if not v.nextMaintainDate:
            lines.append(f"- {label}：未设置下次保养日期，建议引导用户去车辆档案补填。")
            continue
        try:
            due = datetime.strptime(v.nextMaintainDate[:10], "%Y-%m-%d").date()
        except ValueError:
            lines.append(f"- {label}：下次保养日期格式异常（{v.nextMaintainDate}），建议用户核对。")
            continue
        days = (due - today).days
        mileage = f"当前里程 {v.mileage} 公里" if v.mileage is not None else "里程未记录"
        if days < 0:
            lines.append(f"- {label}：下次保养日期 {due} 已超期 {abs(days)} 天（{mileage}），建议尽快预约。")
        elif days <= 30:
            lines.append(f"- {label}：下次保养日期 {due}，还有 {days} 天（{mileage}），建议近期预约。")
        else:
            lines.append(f"- {label}：下次保养日期 {due}，还有 {days} 天（{mileage}），暂不需要。")
    return "\n".join(lines)

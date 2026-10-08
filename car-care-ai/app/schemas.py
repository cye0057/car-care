"""Java ↔ Python 的数据契约。

这个文件是「接口即契约」的落点：Java 侧的 vo/AiUserContextVO 与这里的 UserContext
字段一一对应（camelCase 保持一致），改动任何一侧都要同步另一侧。
"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, Field

# ---------------------------------------------------------------------------
# Java 下发的用户上下文（用户私有数据，按 JWT 身份裁剪，AI 服务只读不改）
# ---------------------------------------------------------------------------


class VehicleBrief(BaseModel):
    """车辆档案：保养建议的核心依据。"""

    id: int
    plateNumber: str | None = None
    brand: str | None = None
    model: str | None = None
    color: str | None = None
    mileage: int | None = Field(default=None, description="当前里程（公里）")
    registerDate: str | None = Field(default=None, description="注册日期 yyyy-MM-dd")
    nextMaintainDate: str | None = Field(default=None, description="下次保养日期 yyyy-MM-dd")


class OrderItemBrief(BaseModel):
    itemName: str
    number: int | None = None
    amount: float | None = None


class OrderBrief(BaseModel):
    orderNo: str
    storeId: int | None = None
    storeName: str | None = None
    status: int
    statusText: str
    totalAmount: float | None = None
    actualAmount: float | None = None
    appointmentTime: str | None = None
    orderTime: str | None = None
    items: list[OrderItemBrief] = Field(default_factory=list)


class WorkOrderBrief(BaseModel):
    """工单进度：回答「我的车修到哪一步了」的数据源。"""

    orderNo: str
    storeId: int | None = None
    status: int
    statusText: str
    technician: str | None = None
    progressDesc: str | None = None
    startTime: str | None = None
    finishTime: str | None = None


class CouponBrief(BaseModel):
    """用户已领取的优惠券。"""

    couponId: int
    storeId: int | None = None
    title: str
    type: int | None = None
    typeDesc: str | None = None
    minPrice: float | None = None
    discountPrice: float | None = None
    cashPrice: float | None = None
    validEndTime: str | None = None


class UserContext(BaseModel):
    """一次对话请求携带的用户私有上下文。

    设计要点：AI 服务不持有用户态，也不按 userId 反查数据库——
    用户数据由 Java 侧用已鉴权的 userId 查好后下发，从根上消除越权读取的可能。
    """

    userId: int | None = None
    name: str | None = None
    city: str | None = None
    vehicles: list[VehicleBrief] = Field(default_factory=list)
    recentOrders: list[OrderBrief] = Field(default_factory=list)
    workOrders: list[WorkOrderBrief] = Field(default_factory=list)
    coupons: list[CouponBrief] = Field(default_factory=list)


# ---------------------------------------------------------------------------
# 请求
# ---------------------------------------------------------------------------


class ChatTurn(BaseModel):
    """历史消息。Java 是会话的唯一持久化方，每次请求把最近若干轮下发过来。"""

    role: Literal["user", "assistant"]
    content: str


class ChatRequest(BaseModel):
    sessionId: str = Field(description="会话 id，由 Java 生成并保证归属")
    message: str = Field(min_length=1, max_length=2000)
    userId: int | None = None
    history: list[ChatTurn] = Field(default_factory=list)
    userContext: UserContext | None = None


# ---------------------------------------------------------------------------
# Agent 内部：预约草稿（工具产出，不落库；用户确认后由前端调 Java 的下单接口）
# ---------------------------------------------------------------------------


class BookingDraft(BaseModel):
    """预约草稿：AI 只负责把「哪个店、做哪些项目、大概多少钱」整理清楚，
    真正的下单仍然走既有的 POST /api/orders（复用订单状态机、库存、优惠券校验）。
    """

    storeId: int
    storeName: str
    itemIds: list[int] = Field(default_factory=list)
    itemNames: list[str] = Field(default_factory=list)
    packageId: int | None = None
    packageName: str | None = None
    vehicleId: int | None = None
    appointmentTime: str | None = Field(default=None, description="建议预约时间 yyyy-MM-dd HH:mm")
    estimatedAmount: float | None = None
    note: str | None = None


# ---------------------------------------------------------------------------
# SSE 事件（Java 逐帧透传给前端，前端按 type 分支渲染）
# ---------------------------------------------------------------------------

EventType = Literal["start", "delta", "reset", "tool_start", "tool_end", "draft", "done", "error"]


class ChatEvent(BaseModel):
    type: EventType
    content: str | None = None
    toolName: str | None = None
    toolLabel: str | None = None
    toolArgs: dict[str, Any] | None = None
    toolOk: bool | None = None
    toolSummary: str | None = None
    draft: BookingDraft | None = None
    latencyMs: int | None = None
    inputTokens: int | None = None
    outputTokens: int | None = None
    message: str | None = None

    def to_sse(self) -> str:
        """序列化成 SSE 帧。Java 侧 WebClient 解码后原样转发，前端拿到的是裸 JSON。"""
        return self.model_dump_json(exclude_none=True)


class HealthResponse(BaseModel):
    status: Literal["ok", "degraded"]
    model: str
    ragReady: bool
    ragChunks: int
    detail: str | None = None


class RagHit(BaseModel):
    text: str
    source: str
    score: float

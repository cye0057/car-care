"""Agent 工具集：新增工具后记得同步 prompts.py 里的「什么时候调用」场景清单。"""

from app.agent.tools.booking import build_booking_draft
from app.agent.tools.catalog import (
    get_packages,
    list_claimable_coupons,
    search_service_items,
    search_stores,
)
from app.agent.tools.context import (
    get_my_coupons,
    get_my_orders,
    get_my_vehicles,
    get_order_progress,
    suggest_maintenance_plan,
)
from app.agent.tools.knowledge import search_maintenance_knowledge

ALL_TOOLS = [
    # 目录类：数据来自 car-care-server 内部只读接口（公共数据）
    search_stores,
    search_service_items,
    get_packages,
    list_claimable_coupons,
    # 用户私有类：数据来自 Java 随请求下发的 userContext
    get_my_vehicles,
    get_my_coupons,
    get_my_orders,
    get_order_progress,
    suggest_maintenance_plan,
    # 知识类：本地 Chroma 向量库
    search_maintenance_knowledge,
    # 动作类：只产出草稿，不落库
    build_booking_draft,
]

__all__ = ["ALL_TOOLS"]

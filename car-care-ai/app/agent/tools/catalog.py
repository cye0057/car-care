"""目录类工具：门店、项目、套餐、优惠券。

数据全部来自 car-care-server 的内部只读接口，因此天然继承 Java 侧的
「营业中门店 / 在售项目 / 启用套餐」过滤口径与 Redis 缓存，不存在两套过滤逻辑。
"""

from __future__ import annotations

from langchain.tools import tool

from app.agent.tools._common import empty_text, failure_text, money, text
from app.clients.carcare import get_client


@tool
async def search_stores(city: str | None = None, keyword: str | None = None) -> str:
    """查询平台上正在营业的维修保养门店。

    用于回答「有哪些门店」「杭州有什么店」「有没有叫 XX 的店」。

    Args:
        city: 城市名，例如「杭州」。用户没提城市时可以留空，留空则返回全部营业门店。
        keyword: 门店名称关键字，例如「滨江」。不确定门店名时留空。
    """
    result = await get_client().search_stores(city=city, keyword=keyword)
    if not result.ok:
        return failure_text("门店查询", result)
    if not result.data:
        scope = f"{text(city, '')}{text(keyword, '')}".strip()
        return empty_text(f"{scope}的营业门店", "可以换个城市或去掉关键字再试。")
    lines = ["查询到以下营业门店："]
    for s in result.data:
        # 地址字段本身常已含城市名（如「杭州市西湖区…」），再拼一次会出现「杭州杭州市…」
        city = text(s.get("city"), "")
        address = text(s.get("address"), "")
        addr = address if not city or city in address else f"{city}{address}"
        lines.append(
            f"- 门店ID={s.get('id')}｜{text(s.get('name'))}｜地址：{addr}"
            f"｜评分：{text(s.get('score'))}｜营业时间：{text(s.get('businessHours'))}"
            f"｜电话：{text(s.get('phone'))}"
        )
    return "\n".join(lines)


@tool
async def search_service_items(store_id: int | None = None, keyword: str | None = None) -> str:
    """查询门店在售的保养/维修项目及其价格。

    用于回答「小保养多少钱」「有哪些项目」「换机油多少钱」。

    Args:
        store_id: 门店 ID。已知用户想去哪家店就带上，只查那家店；不确定就留空查全部。
        keyword: 项目名称关键字，例如「机油」「刹车」。想浏览全部项目时留空。
    """
    result = await get_client().search_items(store_id=store_id, keyword=keyword)
    if not result.ok:
        return failure_text("保养项目查询", result)
    if not result.data:
        return empty_text("符合条件的在售项目", "可以换个说法或去掉门店限制再试。")
    lines = ["查询到以下在售项目："]
    for it in result.data:
        desc = text(it.get("description"), "")
        lines.append(
            f"- 项目ID={it.get('id')}｜门店ID={it.get('storeId')}｜{text(it.get('name'))}"
            f"｜价格：{money(it.get('price'))}" + (f"｜说明：{desc}" if desc else "")
        )
    return "\n".join(lines)


@tool
async def get_packages(store_id: int) -> str:
    """查询指定门店正在售卖的保养套餐及其包含的项目。

    用于回答「有什么套餐」「套餐划算吗」。

    Args:
        store_id: 门店 ID，必填。不知道门店 ID 时先用 search_stores 查出来。
    """
    result = await get_client().list_packages(store_id)
    if not result.ok:
        return failure_text("套餐查询", result)
    if not result.data:
        return empty_text(f"门店 {store_id} 的启用套餐", "该门店暂时没有打包套餐，可以按单项推荐。")
    lines = [f"门店 {store_id} 的套餐："]
    for p in result.data:
        names = p.get("itemNames") or []
        joined = "、".join(names) if names else "明细未标注"
        lines.append(
            f"- 套餐ID={p.get('id')}｜{text(p.get('name'))}｜价格：{money(p.get('price'))}"
            f"｜包含：{joined}"
        )
    return "\n".join(lines)


@tool
async def list_claimable_coupons(store_id: int | None = None) -> str:
    """查询平台上正在发放、可以抢的优惠券（券池），不是用户已领到的券。

    用于回答「有什么优惠活动」「这家店有什么券可以领」。
    想知道用户**已经领到**的券要用 get_my_coupons，两者含义不同。

    Args:
        store_id: 门店 ID。想知道某家店的活动就带上，想查全平台活动则留空。
    """
    result = await get_client().list_coupons(store_id=store_id)
    if not result.ok:
        return failure_text("优惠券活动查询", result)
    if not result.data:
        return empty_text("正在发放的优惠券", "该范围内暂时没有可领的券。")
    lines = ["平台正在发放的优惠券（需用户在活动页领取后才能在订单里使用）："]
    for c in result.data:
        lines.append(
            f"- 券ID={c.get('id')}｜{text(c.get('title'))}"
            f"｜类型：{text(c.get('typeDesc'), '优惠券')}"
            f"｜满：{money(c.get('minPrice'))}｜减：{money(c.get('discountPrice') or c.get('cashPrice'))}"
            f"｜剩余库存：{text(c.get('stock'))}"
            f"｜有效期至：{text(c.get('validEndTime'))}"
            f"｜适用门店ID：{text(c.get('storeId'), '全部门店')}"
        )
    return "\n".join(lines)

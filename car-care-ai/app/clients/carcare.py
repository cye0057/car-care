"""调用 car-care-server 的内部只读接口。

为什么不让 AI 服务直连 MySQL：
1. 「营业中门店 / 在售项目」这些过滤口径只在 Java 侧维护一份，避免两处实现漂移；
2. Java 侧的 Redis 缓存（门店列表、详情逻辑过期）能直接复用，AI 查询不打 DB；
3. AI 服务不持有数据源凭证，出事时的爆炸半径更小。

所有方法都返回 ToolResult 而不是裸 list：工具需要区分「确实没有符合条件的数据」和
「上游接口挂了」——前者如实告诉用户，后者必须说「查询服务暂时不可用」，不能编造。
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Any

import httpx

from app.config import settings
from app.security import HEADER_NAME

log = logging.getLogger(__name__)


@dataclass
class ToolResult:
    """工具查询结果：ok=False 时 data 一定为空，error 是可读的失败原因。"""

    ok: bool
    data: list[dict[str, Any]] = field(default_factory=list)
    error: str | None = None

    @classmethod
    def success(cls, data: list[dict[str, Any]]) -> "ToolResult":
        return cls(ok=True, data=data)

    @classmethod
    def failure(cls, error: str) -> "ToolResult":
        return cls(ok=False, error=error)


class CarCareClient:
    """car-care-server 内部接口的异步客户端（进程内单例，由 lifespan 关闭）。"""

    def __init__(self) -> None:
        self._client = httpx.AsyncClient(
            base_url=settings.carcare_api_base_url.rstrip("/"),
            headers={HEADER_NAME: settings.carcare_internal_token},
            timeout=httpx.Timeout(settings.carcare_api_timeout),
        )

    async def aclose(self) -> None:
        await self._client.aclose()

    async def _get_list(self, path: str, params: dict[str, Any]) -> ToolResult:
        # 去掉 None 参数：避免把 storeId=null 这类空条件透传给 Java
        clean = {k: v for k, v in params.items() if v not in (None, "")}
        try:
            resp = await self._client.get(path, params=clean)
            resp.raise_for_status()
            body = resp.json()
        except httpx.TimeoutException:
            log.warning("内部接口超时 path=%s params=%s", path, clean)
            return ToolResult.failure("车管家数据服务响应超时")
        except httpx.HTTPStatusError as e:
            log.warning("内部接口返回 %s path=%s body=%s", e.response.status_code, path, e.response.text[:200])
            return ToolResult.failure(f"车管家数据服务返回异常（HTTP {e.response.status_code}）")
        except Exception as e:  # 连接被拒、JSON 解析失败等
            log.warning("内部接口调用失败 path=%s err=%s", path, e)
            return ToolResult.failure("车管家数据服务暂时不可用")

        if body.get("code") != 1:
            log.warning("内部接口业务失败 path=%s msg=%s", path, body.get("msg"))
            return ToolResult.failure(body.get("msg") or "车管家数据服务返回业务失败")

        data = body.get("data")
        if isinstance(data, list):
            return ToolResult.success(data)
        return ToolResult.success([])

    # ------------------------------------------------------------------
    # 以下 4 个方法对应 Java 侧 /api/internal/ai/** 的 4 个只读接口
    # ------------------------------------------------------------------

    async def search_stores(self, city: str | None = None, keyword: str | None = None,
                            store_id: int | None = None, limit: int = 5) -> ToolResult:
        """store_id 走 id 点查（Java 侧不受 limit 约束），用于按 id 取门店名。"""
        return await self._get_list("/api/internal/ai/stores",
                                    {"id": store_id, "city": city, "keyword": keyword, "limit": limit})

    async def search_items(self, store_id: int | None = None, keyword: str | None = None,
                           ids: list[int] | None = None, limit: int = 10) -> ToolResult:
        """ids 走 id 点查（Java 侧不分页），用于校验指定项目是否存在、属于哪家门店。"""
        return await self._get_list("/api/internal/ai/items",
                                    {"ids": ",".join(str(i) for i in ids) if ids else None,
                                     "storeId": store_id, "keyword": keyword, "limit": limit})

    async def list_packages(self, store_id: int, limit: int = 5) -> ToolResult:
        return await self._get_list("/api/internal/ai/packages",
                                    {"storeId": store_id, "limit": limit})

    async def list_coupons(self, store_id: int | None = None, user_id: int | None = None,
                           limit: int = 5) -> ToolResult:
        return await self._get_list("/api/internal/ai/coupons",
                                    {"storeId": store_id, "userId": user_id, "limit": limit})


_client: CarCareClient | None = None


def get_client() -> CarCareClient:
    global _client
    if _client is None:
        _client = CarCareClient()
    return _client


async def close_client() -> None:
    global _client
    if _client is not None:
        await _client.aclose()
        _client = None

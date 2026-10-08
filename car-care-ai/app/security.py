"""内部令牌校验。

AI 服务只绑 127.0.0.1，唯一的调用方是 car-care-server。这里再加一道共享令牌，
是为了防「同机其他进程 / 误配端口转发」把服务暴露出去——
只靠绑回环地址在容器或反向代理场景下并不总是成立。
"""

from __future__ import annotations

import secrets
from typing import Annotated

from fastapi import Header, HTTPException, status

from app.config import settings

HEADER_NAME = "X-Internal-Token"


async def require_internal_token(
    x_internal_token: Annotated[str | None, Header(alias=HEADER_NAME)] = None,
) -> None:
    expected = settings.carcare_internal_token
    if not expected:
        # 宁可整个服务不可用，也不要「配置漏了 = 完全开放」
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="服务端未配置 CARCARE_INTERNAL_TOKEN",
        )
    # compare_digest 避免按字符短路比较带来的时序侧信道
    if not x_internal_token or not secrets.compare_digest(x_internal_token, expected):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="内部令牌校验失败",
        )

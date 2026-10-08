"""pytest 公共配置。

只把项目根加进 sys.path，让测试能 import app.*；
不引入 pytest-asyncio —— 异步用例统一用 asyncio.run() 包一层，
少一个依赖，也避免 asyncio_mode 之类的配置分歧。
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


@pytest.fixture(autouse=True)
def _quiet_logs(monkeypatch):
    """测试里不需要 INFO 日志刷屏。"""
    import logging
    logging.getLogger("app").setLevel(logging.WARNING)

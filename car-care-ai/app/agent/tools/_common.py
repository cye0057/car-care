"""工具层共用的格式化与失败文案。

工具返回值是**给模型看的自然语言**，不是给前端渲染的 JSON——
把结构化数据压成紧凑的短文本能省 token，同时明确标出失败原因，
让模型能区分「没查到」和「查询挂了」并如实转述（这条约束写在系统提示词里）。
"""

from __future__ import annotations

from typing import Any

from app.clients.carcare import ToolResult


def failure_text(what: str, result: ToolResult) -> str:
    return (f"{what}失败：{result.error}。"
            f"请如实告诉用户暂时查不到这项信息、建议稍后再试，禁止编造任何数据。")


def empty_text(what: str, hint: str = "") -> str:
    return f"没有查询到{what}。{hint}".strip()


def money(value: Any) -> str:
    if value is None:
        return "未标注"
    try:
        return f"¥{float(value):.2f}"
    except (TypeError, ValueError):
        return str(value)


def text(value: Any, default: str = "未标注") -> str:
    if value is None:
        return default
    value = str(value).strip()
    return value or default

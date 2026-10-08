"""车管家 AI 助手 SSE 排障脚本。

用途：绕过前端直接观察 /api/ai/chat 推回来的每一帧事件，用来确认
「流式是否真流式、工具调了哪些、reset 有没有生效、token 花了多少」。

用法（仓库根目录下）：
    python document/tools/sse_probe.py -q "杭州有哪些门店？"
    python document/tools/sse_probe.py -q "我的车该保养了吗？" -s <已有 sessionId>
    python document/tools/sse_probe.py -q "继续" --raw      # 打印原始 SSE 帧

依赖：requests（pip install requests）。脚本只依赖标准库 + requests，不引项目代码。
"""

from __future__ import annotations

import argparse
import json
import sys

import requests

DEFAULT_BASE = "http://127.0.0.1:8082"
DEFAULT_USER = "zhangsan"
DEFAULT_PASSWORD = "123456"


def login(base: str, username: str, password: str) -> str:
    resp = requests.post(f"{base}/api/auth/login",
                         json={"username": username, "password": password}, timeout=10)
    resp.raise_for_status()
    body = resp.json()
    if body.get("code") != 1:
        raise SystemExit(f"登录失败：{body.get('msg')}")
    return body["data"]["token"]


def stream_chat(base: str, token: str, question: str, session_id: str | None, raw: bool) -> str | None:
    payload = {"message": question}
    if session_id:
        payload["sessionId"] = session_id
    with requests.post(f"{base}/api/ai/chat",
                       headers={"token": token, "Content-Type": "application/json; charset=utf-8"},
                       json=payload, stream=True, timeout=300) as resp:
        if resp.status_code != 200:
            raise SystemExit(f"HTTP {resp.status_code}: {resp.text[:300]}")
        # 后端已显式声明 charset=UTF-8；这里再兜一次，防止中间层把 charset 丢掉导致中文乱码
        resp.encoding = "utf-8"
        result_session = session_id
        for line in resp.iter_lines(decode_unicode=True):
            if not line or not line.startswith("data:"):
                continue
            data = line[5:].strip()
            if not data:
                continue
            if raw:
                print(f"RAW  {data[:200]}")
            try:
                event = json.loads(data)
            except json.JSONDecodeError as e:
                print(f"  !! 无法解析的帧（{e.msg}）: {data[:300]}")
                continue
            kind = event.get("type")
            if kind == "start":
                result_session = event.get("sessionId")
                print(f"[start]  session={result_session}")
            elif kind == "delta":
                print(event.get("content") or "", end="", flush=True)
            elif kind == "reset":
                print("\n[reset]  已丢弃本轮过渡语（工具调用前的自述）")
            elif kind == "tool_start":
                print(f"\n[tool]   → {event.get('toolLabel')} ({event.get('toolName')}) args={event.get('toolArgs')}")
            elif kind == "tool_end":
                flag = "OK " if event.get("toolOk") else "FAIL"
                print(f"[tool]   ← {flag} {event.get('toolSummary')}")
            elif kind == "draft":
                print(f"[draft]  {json.dumps(event.get('draft'), ensure_ascii=False)}")
            elif kind == "done":
                print(f"\n[done]   latency={event.get('latencyMs')}ms "
                      f"tokens={event.get('inputTokens')}/{event.get('outputTokens')}")
            elif kind == "error":
                print(f"\n[error]  {event.get('message')}")
            else:
                print(f"[{kind}] {event}")
        return result_session


def main() -> int:
    # Windows 控制台默认 GBK，中文输出会乱码或直接抛 UnicodeEncodeError，强制切到 UTF-8
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="车管家 AI 助手 SSE 排障脚本")
    parser.add_argument("-q", "--question", required=True, help="要问的问题")
    parser.add_argument("-s", "--session", help="已有会话 id（不传则新建）")
    parser.add_argument("--base", default=DEFAULT_BASE, help=f"后端地址，默认 {DEFAULT_BASE}")
    parser.add_argument("--user", default=DEFAULT_USER, help=f"登录账号，默认 {DEFAULT_USER}")
    parser.add_argument("--password", default=DEFAULT_PASSWORD, help="登录密码")
    parser.add_argument("--raw", action="store_true", help="额外打印原始帧")
    args = parser.parse_args()

    token = login(args.base, args.user, args.password)
    session = stream_chat(args.base, token, args.question, args.session, args.raw)
    print(f"\n--- 会话 id: {session}（下次加 -s 可继续这段对话）")
    return 0


if __name__ == "__main__":
    sys.exit(main())

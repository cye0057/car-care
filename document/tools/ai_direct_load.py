"""AI 助手并发瓶颈定位脚本（对照实验用）。

用途：绕过 Java 网关直连 Python 服务的 /v1/chat，用与 JMeter 相同的并发度
和相同的问题压一遍，用来回答一个问题：

    50 并发时延迟从 1.4s 涨到 4.8s，到底是 Java 侧的锅，还是上游大模型吞吐的锅？

判定方法：如果直连 Python 也是同样的延迟，说明瓶颈在上游 LLM（Java 只是透传，
没引入额外排队）；如果直连明显更快，那瓶颈就在 Java 侧。

用法（仓库根目录下）：
    python document/tools/ai_direct_load.py -c 20
    python document/tools/ai_direct_load.py -c 50 -q "杭州有哪些门店？"

依赖：requests。只读 car-care-ai/.env 取内部令牌，不引项目代码。
"""

from __future__ import annotations

import argparse
import json
import statistics
import sys
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import requests

ENV_PATH = Path(__file__).resolve().parents[2] / "car-care-ai" / ".env"


def read_internal_token() -> str:
    if not ENV_PATH.exists():
        raise SystemExit(f"找不到 {ENV_PATH}，无法读取 CARCARE_INTERNAL_TOKEN")
    for line in ENV_PATH.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line.startswith("CARCARE_INTERNAL_TOKEN="):
            return line.split("=", 1)[1].strip().strip('"')
    raise SystemExit("car-care-ai/.env 里没有 CARCARE_INTERNAL_TOKEN")


def one_call(base: str, token: str, question: str) -> tuple[float, bool, str]:
    """发一次流式对话，返回 (耗时毫秒, 是否拿到 done, 备注)。"""
    payload = {"sessionId": uuid.uuid4().hex, "message": question}
    started = time.perf_counter()
    done = False
    note = ""
    try:
        with requests.post(f"{base}/v1/chat",
                           headers={"X-Internal-Token": token,
                                    "Content-Type": "application/json; charset=utf-8"},
                           json=payload, stream=True, timeout=300) as resp:
            if resp.status_code != 200:
                return (time.perf_counter() - started) * 1000, False, f"HTTP {resp.status_code}"
            resp.encoding = "utf-8"
            for line in resp.iter_lines(decode_unicode=True):
                if not line or not line.startswith("data:"):
                    continue
                try:
                    ev = json.loads(line[5:].strip())
                except json.JSONDecodeError:
                    continue
                kind = ev.get("type")
                if kind == "done":
                    done = True
                elif kind == "error":
                    note = str(ev.get("message"))[:60]
    except Exception as e:  # 网络异常也要计入错误率，不能吞掉
        return (time.perf_counter() - started) * 1000, False, type(e).__name__
    return (time.perf_counter() - started) * 1000, done, note


def main() -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    p = argparse.ArgumentParser(description="直连 Python AI 服务的并发对照压测")
    p.add_argument("-c", "--concurrency", type=int, default=20, help="并发数")
    p.add_argument("-q", "--question", default="你好，你能做什么？", help="提问内容")
    p.add_argument("--base", default="http://127.0.0.1:8000", help="Python 服务地址")
    args = p.parse_args()

    token = read_internal_token()
    print(f"并发 {args.concurrency} ｜ 直连 {args.base}/v1/chat ｜ 问题：{args.question}")

    started = time.perf_counter()
    with ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        results = list(pool.map(lambda _: one_call(args.base, token, args.question),
                                range(args.concurrency)))
    wall = time.perf_counter() - started

    lat = sorted(r[0] for r in results)
    n = len(lat)
    errs = [r for r in results if not r[1]]
    q = lambda p_: lat[min(n - 1, int(n * p_))]

    print(f"\n总耗时 {wall:.2f}s ｜ 吞吐 {n / wall:.1f}/s")
    print(f"n={n} err={len(errs)}  avg={statistics.mean(lat):.0f}ms  "
          f"p50={q(.5):.0f}  p90={q(.9):.0f}  p99={q(.99):.0f}  "
          f"min={lat[0]:.0f}  max={lat[-1]:.0f}  离散度={lat[-1] / lat[0]:.2f}")
    for _, _, note in errs[:5]:
        print(f"  失败样本：{note}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

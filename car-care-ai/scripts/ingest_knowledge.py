"""重建知识库 CLI。

用法（在 car-care-ai/ 目录下）：
    python scripts/ingest_knowledge.py

什么时候需要跑：
- 首次部署（也可以不跑，服务启动时会自动灌一次空库）
- 改了 app/knowledge/*.md 之后
- 换了 embedding 模型（维度变了必须重建，否则检索结果全是噪声）

重建会消耗 embedding 额度，别在压测循环里调它。
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import settings  # noqa: E402
from app.rag import store  # noqa: E402
from app.rag.ingest import load_chunks  # noqa: E402


def main() -> int:
    print(f"语料目录: {settings.knowledge_dir}")
    print(f"向量库目录: {settings.chroma_path}")
    print(f"embedding: {settings.embedding_model} @ {settings.embedding_base_url}")

    if not settings.rag_available:
        print("RAG 未启用或缺少 EMBEDDING_API_KEY，请检查 .env")
        return 1

    chunks = load_chunks()
    print(f"切分出 {len(chunks)} 个分片，开始写入…")
    total = store.rebuild(chunks)
    print(f"完成，向量库现有 {total} 个分片")
    return 0 if total > 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())

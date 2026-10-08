"""语料加载与切分。

切分策略（沿用 langchain1.2 里验证过的思路，并针对保养语料做了调整）：
1. 先按 markdown 的 `## ` 二级标题切成章节——保养知识天然按「机油/轮胎/刹车」分节，
   按标题切比按字数硬切更不容易把一条完整规则拆散；
2. 章节过长再走 RecursiveCharacterTextSplitter，分隔符按中文标点优先，避免在句子中间断开；
3. 每个分片前面拼上 `【文档名·章节名】`，这段前缀同样参与向量化——
   实测能显著提升召回（用户问「刹车片多久换」时，带主题前缀的分片更容易被选中）；
4. 正文短于 MIN_SECTION_CHARS 的章节直接丢弃（通常是只有标题没内容的占位节）。
"""

from __future__ import annotations

import logging
import re

from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.config import settings

log = logging.getLogger(__name__)

MIN_SECTION_CHARS = 30
CHUNK_SIZE = 350
CHUNK_OVERLAP = 60

# 中文语料的分隔优先级：段落 → 换行 → 句号 → 分号 → 逗号 → 空格
_SPLITTER = RecursiveCharacterTextSplitter(
    chunk_size=CHUNK_SIZE,
    chunk_overlap=CHUNK_OVERLAP,
    separators=["\n\n", "\n", "。", "；", "，", " ", ""],
    keep_separator="end",
)

_H1 = re.compile(r"^#\s+(.+)$", re.MULTILINE)
_H2_SPLIT = re.compile(r"^##\s+(.+)$", re.MULTILINE)


def _doc_title(text: str, fallback: str) -> str:
    """取一级标题作为文档名；没有就退回文件名。"""
    m = _H1.search(text)
    return m.group(1).strip() if m else fallback


def _split_sections(text: str) -> list[tuple[str, str]]:
    """按 `## ` 切成 (章节标题, 章节正文)。首个 ## 之前的内容归入「概述」。"""
    matches = list(_H2_SPLIT.finditer(text))
    if not matches:
        return [("概述", text)]

    sections: list[tuple[str, str]] = []
    head = text[: matches[0].start()].strip()
    # 去掉一级标题行本身，它已经作为文档名参与前缀了
    head = _H1.sub("", head, count=1).strip()
    if head:
        sections.append(("概述", head))

    for i, m in enumerate(matches):
        title = m.group(1).strip()
        body_start = m.end()
        body_end = matches[i + 1].start() if i + 1 < len(matches) else len(text)
        sections.append((title, text[body_start:body_end].strip()))
    return sections


def load_chunks() -> list[Document]:
    """把 app/knowledge/*.md 全部加载成带来源元数据的分片。"""
    if not settings.knowledge_dir.is_dir():
        log.warning("语料目录不存在: %s", settings.knowledge_dir)
        return []

    documents: list[Document] = []
    for path in sorted(settings.knowledge_dir.glob("*.md")):
        raw = path.read_text(encoding="utf-8")
        doc_title = _doc_title(raw, path.stem)
        for section_title, body in _split_sections(raw):
            if len(body) < MIN_SECTION_CHARS:
                continue
            for piece in _SPLITTER.split_text(body):
                piece = piece.strip()
                if len(piece) < MIN_SECTION_CHARS:
                    continue
                documents.append(Document(
                    page_content=f"【{doc_title}·{section_title}】{piece}",
                    metadata={"source": path.name, "section": section_title},
                ))
    log.info("语料加载完成：%d 个分片", len(documents))
    return documents

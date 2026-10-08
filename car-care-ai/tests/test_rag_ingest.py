"""RAG 语料切分测试：这部分纯字符串处理，不联网，最适合用测试兜住。

重点验证三件事（都是踩过坑的地方）：
1. 分片前缀带上了【文档·章节】，检索召回依赖它；
2. 只有标题没有正文的章节会被丢掉，不然向量库里全是噪声；
3. 真实语料能切出合理数量的分片（防止有人改了分隔符导致只切出一片）。
"""

from __future__ import annotations

from app.rag.ingest import MIN_SECTION_CHARS, _doc_title, _split_sections, load_chunks


def test_doc_title_falls_back_to_filename():
    assert _doc_title("# 汽车保养周期标准\n\n正文", "fallback") == "汽车保养周期标准"
    assert _doc_title("没有一级标题的正文", "fallback") == "fallback"


def test_split_sections_keeps_head_as_overview():
    text = "# 标题\n\n开头说明。\n\n## 第一节\n\n内容一。\n\n## 第二节\n\n内容二。"
    sections = _split_sections(text)
    assert [s[0] for s in sections] == ["概述", "第一节", "第二节"]
    # 一级标题本身不该留在正文里，它已经作为文档名参与前缀
    assert "标题" not in sections[0][1]
    assert sections[1][1] == "内容一。"


def test_split_sections_without_h2_returns_single_overview():
    assert _split_sections("只有一段没有小标题的正文") == [("概述", "只有一段没有小标题的正文")]


def test_load_chunks_prefixes_and_drops_stubs():
    chunks = load_chunks()
    assert chunks, "语料目录应该能切出分片"

    for doc in chunks:
        assert doc.page_content.startswith("【"), "每个分片都要带【文档·章节】前缀"
        assert "·" in doc.page_content.split("】")[0]
        assert doc.metadata["source"].endswith(".md")
        assert len(doc.page_content) >= MIN_SECTION_CHARS

    # 5 篇语料、每篇若干章节，切出来应该是几十片而不是一片或上千片
    assert 20 <= len(chunks) <= 200, f"分片数量异常: {len(chunks)}"


def test_all_knowledge_files_are_loaded():
    chunks = load_chunks()
    sources = {d.metadata["source"] for d in chunks}
    assert sources == {
        "maintenance-cycle.md",
        "tires-and-brakes.md",
        "warning-lights.md",
        "seasonal-care.md",
        "platform-rules.md",
    }

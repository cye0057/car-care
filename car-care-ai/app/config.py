"""全局配置：从 .env / 环境变量读取，进程内单例。

放在 app/config.py 而不是散落的 os.getenv，是为了让「有哪些可调参数、默认值是多少」
集中在一处可查——langchain1.2 练习里每个 notebook 都重复 6 行 os.getenv，这里收敛掉。
"""

from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

# car-care-ai/ 目录：.env 与 data/ 都相对它定位，避免依赖启动时的 CWD
BASE_DIR = Path(__file__).resolve().parents[1]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=BASE_DIR / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    # ---------- 对话模型 ----------
    llm_model: str = "deepseek:deepseek-chat"
    llm_api_key: str = ""
    llm_base_url: str = "https://api.deepseek.com"
    llm_temperature: float = 0.3
    llm_disable_thinking: bool = True

    # ---------- Embedding / RAG ----------
    embedding_model: str = "BAAI/bge-m3"
    embedding_api_key: str = ""
    embedding_base_url: str = "https://api.siliconflow.cn/v1"
    rag_enabled: bool = True
    rag_top_k: int = 4
    # 实测标定值（bge-m3 + 本项目语料）：相关问题命中 0.43~0.68，无关问题 0.18~0.25。
    # 取 0.35 落在两簇之间的空档：既能保住「安心保」这类得分偏低的平台规则类命中，
    # 又能滤掉闲聊问题带来的噪声。换 embedding 模型或大改语料后要重新标定。
    rag_min_score: float = 0.35
    rag_ingest_on_startup: bool = True
    chroma_dir: str = "./data/chroma"
    chroma_collection: str = "carcare_knowledge"

    # ---------- Java 内部只读接口 ----------
    carcare_api_base_url: str = "http://127.0.0.1:8082"
    carcare_internal_token: str = ""
    carcare_api_timeout: float = 8.0

    # ---------- Agent 预算 ----------
    # 工具轮数上限。定这个值的依据：一次完整的预约咨询可能要走
    # 查门店 → 查项目 → 看车辆 → 看券 → 出草稿，实测用掉 4 轮，
    # 再留 1 轮给最终回答，所以给 5（实际模型调用上限 = 它 + 1）。
    # 调太小会出现「工具都调完了但没机会说话」，最终回答变成兜底文案。
    max_tool_rounds: int = 5
    request_timeout: float = 120.0

    # ---------- 服务 ----------
    host: str = "127.0.0.1"
    port: int = 8000
    log_level: str = "INFO"

    @property
    def chroma_path(self) -> Path:
        path = Path(self.chroma_dir)
        return path if path.is_absolute() else (BASE_DIR / path).resolve()

    @property
    def knowledge_dir(self) -> Path:
        return BASE_DIR / "app" / "knowledge"

    @property
    def rag_available(self) -> bool:
        """RAG 可用的前提：开关打开 + 有 embedding 密钥 + 语料目录存在。"""
        return self.rag_enabled and bool(self.embedding_api_key) and self.knowledge_dir.is_dir()


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()

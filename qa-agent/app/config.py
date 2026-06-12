"""Settings from shared config/config.json (same file as Java backend)."""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from typing import Any

from app.config_loader import get_float, get_int, get_str, load_flat_config


def _env(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


def _env_int(name: str, default: int) -> int:
    raw = os.environ.get(name)
    if raw is None or not raw.strip():
        return default
    try:
        return int(raw.strip())
    except ValueError:
        return default


@dataclass
class Settings:
    """Merged: config.json (primary) → QA_AGENT_* env (optional override)."""

    config_json_path: str = ""

    # glm.*
    glm_api_key: str = ""
    glm_chat_url: str = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    glm_chat_model: str = "glm-4-flash"
    glm_timeout_seconds: int = 60

    # database.*
    mysql_host: str = "127.0.0.1"
    mysql_port: int = 3306
    mysql_user: str = "archive_app"
    mysql_password: str = ""
    mysql_database: str = "archive_db"

    # storage.*
    file_root: str = "D:/archive/files"
    parsed_root: str = "D:/archive/parsed"
    log_root: str = "D:/archive/logs"

    # qaAgent.*
    qa_agent_host: str = "127.0.0.1"
    qa_agent_port: int = 8001
    agent_max_iterations: int = 5

    # archive.networkDict.*
    network_dict_enabled_sources: list[str] = field(default_factory=lambda: ["baidu_baike", "wiki"])
    network_dict_timeout_ms: int = 5000
    network_dict_cache_ttl: int = 3600

    # archive.queryMysql.*
    query_mysql_max_rows: int = 1000
    query_mysql_max_amount: float = 1.0e8

    @classmethod
    def load(cls) -> Settings:
        from app.config_loader import resolve_config_path

        path = resolve_config_path()
        flat = load_flat_config()

        enabled_sources_raw = flat.get("archive.networkDict.enabledSources")
        if isinstance(enabled_sources_raw, str):
            try:
                import json

                enabled_sources = json.loads(enabled_sources_raw)
            except json.JSONDecodeError:
                enabled_sources = ["baidu_baike", "wiki"]
        elif isinstance(enabled_sources_raw, list):
            enabled_sources = enabled_sources_raw
        else:
            enabled_sources = ["baidu_baike", "wiki"]

        s = cls(
            config_json_path=str(path.resolve()) if path else "",
            glm_api_key=get_str(flat, "glm.apiKey"),
            glm_chat_url=get_str(
                flat, "glm.chatUrl", "https://open.bigmodel.cn/api/paas/v4/chat/completions"
            ),
            glm_chat_model=get_str(flat, "glm.chatModel", "glm-4-flash"),
            glm_timeout_seconds=get_int(flat, "glm.timeoutSeconds", 60),
            mysql_host=get_str(flat, "database.host", "127.0.0.1"),
            mysql_port=get_int(flat, "database.port", 3306),
            mysql_user=get_str(flat, "database.username", "archive_app"),
            mysql_password=get_str(flat, "database.password"),
            mysql_database=get_str(flat, "database.database", "archive_db"),
            file_root=get_str(flat, "storage.fileRoot", "D:/archive/files"),
            parsed_root=get_str(flat, "storage.parsedRoot", "D:/archive/parsed"),
            log_root=get_str(flat, "storage.logRoot", "D:/archive/logs"),
            qa_agent_host=get_str(flat, "qaAgent.host", "127.0.0.1"),
            qa_agent_port=get_int(flat, "qaAgent.port", 8001),
            agent_max_iterations=get_int(flat, "qaAgent.maxIterations", 5),
            network_dict_enabled_sources=enabled_sources,
            network_dict_timeout_ms=get_int(flat, "archive.networkDict.timeout", 5000),
            network_dict_cache_ttl=get_int(flat, "archive.networkDict.cacheTtl", 3600),
            query_mysql_max_rows=get_int(flat, "archive.queryMysql.maxResultRows", 1000),
            query_mysql_max_amount=get_float(flat, "archive.queryMysql.maxAmount", 1.0e8),
        )

        if _env("GLM_API_KEY"):
            s.glm_api_key = _env("GLM_API_KEY")
        if _env("GLM_CHAT_URL"):
            s.glm_chat_url = _env("GLM_CHAT_URL")
        if _env("GLM_CHAT_MODEL"):
            s.glm_chat_model = _env("GLM_CHAT_MODEL")
        if _env("GLM_TIMEOUT_SECONDS"):
            s.glm_timeout_seconds = _env_int("GLM_TIMEOUT_SECONDS", s.glm_timeout_seconds)
        if _env("MYSQL_HOST"):
            s.mysql_host = _env("MYSQL_HOST")
        if _env("MYSQL_PORT"):
            s.mysql_port = _env_int("MYSQL_PORT", s.mysql_port)
        if _env("MYSQL_USER"):
            s.mysql_user = _env("MYSQL_USER")
        if _env("MYSQL_PASSWORD"):
            s.mysql_password = _env("MYSQL_PASSWORD")
        if _env("MYSQL_DATABASE"):
            s.mysql_database = _env("MYSQL_DATABASE")
        if _env("FILE_ROOT"):
            s.file_root = _env("FILE_ROOT")
        if _env("PARSED_ROOT"):
            s.parsed_root = _env("PARSED_ROOT")
        if _env("QA_AGENT_HOST"):
            s.qa_agent_host = _env("QA_AGENT_HOST")
        if _env("QA_AGENT_PORT"):
            s.qa_agent_port = _env_int("QA_AGENT_PORT", s.qa_agent_port)
        if _env("AGENT_MAX_ITERATIONS"):
            s.agent_max_iterations = _env_int("AGENT_MAX_ITERATIONS", s.agent_max_iterations)

        return s


settings = Settings.load()

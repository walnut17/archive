"""Default URLs for qa-agent live / smoke tests."""

from __future__ import annotations

import os

# qa-agent 部署在 125；Auto-test 从开发机直连此地址
DEFAULT_QA_AGENT_URL = "http://182.168.1.125:8001"
LOCAL_QA_AGENT_URL = "http://127.0.0.1:8001"


def resolve_base_url() -> str:
    return os.environ.get("QA_AGENT_BASE_URL", DEFAULT_QA_AGENT_URL).rstrip("/")

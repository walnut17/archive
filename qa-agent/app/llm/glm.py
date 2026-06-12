import json
import logging
from typing import Any

import httpx

from app.config import settings

logger = logging.getLogger(__name__)


class GlmClient:
    """智谱 GLM OpenAI 兼容 chat completions."""

    def __init__(self) -> None:
        self._client = httpx.Client(timeout=settings.glm_timeout_seconds)

    def chat(self, system: str, user: str) -> str:
        if not settings.glm_api_key:
            raise RuntimeError("GLM_API_KEY 未配置")

        payload: dict[str, Any] = {
            "model": settings.glm_chat_model,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            "temperature": 0.3,
        }
        headers = {
            "Authorization": f"Bearer {settings.glm_api_key}",
            "Content-Type": "application/json",
        }
        resp = self._client.post(settings.glm_chat_url, json=payload, headers=headers)
        resp.raise_for_status()
        data = resp.json()
        choices = data.get("choices") or []
        if not choices:
            raise RuntimeError("GLM 返回空 choices")
        content = choices[0].get("message", {}).get("content")
        if not content:
            raise RuntimeError("GLM 返回空 content")
        return str(content).strip()


glm_client = GlmClient()

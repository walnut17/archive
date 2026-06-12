import json
import logging
from typing import Any, Iterator

import httpx

from app.config import settings

logger = logging.getLogger(__name__)


class GlmClient:
    """智谱 GLM OpenAI 兼容 chat completions (含流式)."""

    def __init__(self) -> None:
        self._client = httpx.Client(timeout=settings.glm_timeout_seconds)

    def chat(self, system: str, user: str) -> str:
        """非流式：拼完整答案返回."""
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

    def chat_stream(self, system: str, user: str) -> Iterator[str]:
        """流式：逐 token yield (OpenAI SSE 协议)."""
        if not settings.glm_api_key:
            raise RuntimeError("GLM_API_KEY 未配置")

        payload: dict[str, Any] = {
            "model": settings.glm_chat_model,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
            "temperature": 0.3,
            "stream": True,
        }
        headers = {
            "Authorization": f"Bearer {settings.glm_api_key}",
            "Content-Type": "application/json",
            "Accept": "text/event-stream",
        }

        with self._client.stream("POST", settings.glm_chat_url, json=payload, headers=headers) as resp:
            resp.raise_for_status()
            for line in resp.iter_lines():
                if not line:
                    continue
                if line.startswith("data:"):
                    data = line[5:].strip()
                    if data == "[DONE]":
                        break
                    try:
                        chunk = json.loads(data)
                        choices = chunk.get("choices") or []
                        if choices:
                            delta = choices[0].get("delta") or {}
                            content = delta.get("content")
                            if content:
                                yield str(content)
                    except (json.JSONDecodeError, KeyError, IndexError) as e:
                        logger.debug("SSE chunk parse skip: %s", e)
                        continue


glm_client = GlmClient()

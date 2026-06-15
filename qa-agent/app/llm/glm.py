import json
import logging
from typing import Any, Iterator

import httpx

from app.config import settings
from app.services.desensitize import StreamUnmasker, mask_llm_messages, unmask_text

logger = logging.getLogger(__name__)


class GlmClient:
    """智谱 GLM OpenAI 兼容 chat completions (含流式)."""

    def __init__(self) -> None:
        self._client = httpx.Client(timeout=settings.glm_timeout_seconds)

    def _prepare_messages(self, system: str, user: str) -> tuple[str, str, dict[str, str]]:
        if not settings.llm_desensitize_enabled:
            return system, user, {}
        masked_system, masked_user, mapping = mask_llm_messages(system, user)
        if mapping:
            logger.debug("LLM 脱敏: %d 处", len(mapping))
        return masked_system, masked_user, mapping

    def _post_chat(self, system: str, user: str) -> dict[str, Any]:
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
        return resp.json()

    def chat(self, system: str, user: str) -> str:
        """非流式：拼完整答案返回（调用前脱敏，返回前还原）."""
        system_send, user_send, mapping = self._prepare_messages(system, user)
        data = self._post_chat(system_send, user_send)
        choices = data.get("choices") or []
        if not choices:
            raise RuntimeError("GLM 返回空 choices")
        content = choices[0].get("message", {}).get("content")
        if not content:
            raise RuntimeError("GLM 返回空 content")
        raw = str(content).strip()
        return unmask_text(raw, mapping) if mapping else raw

    def chat_stream(self, system: str, user: str) -> Iterator[str]:
        """流式：逐 token yield（调用前脱敏，输出流还原）."""
        if not settings.glm_api_key:
            raise RuntimeError("GLM_API_KEY 未配置")

        system_send, user_send, mapping = self._prepare_messages(system, user)
        unmasker = StreamUnmasker(mapping) if mapping else None

        payload: dict[str, Any] = {
            "model": settings.glm_chat_model,
            "messages": [
                {"role": "system", "content": system_send},
                {"role": "user", "content": user_send},
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
                                piece = str(content)
                                if unmasker:
                                    out = unmasker.feed(piece)
                                    if out:
                                        yield out
                                else:
                                    yield piece
                    except (json.JSONDecodeError, KeyError, IndexError) as e:
                        logger.debug("SSE chunk parse skip: %s", e)
                        continue

        if unmasker:
            tail = unmasker.flush()
            if tail:
                yield tail


glm_client = GlmClient()

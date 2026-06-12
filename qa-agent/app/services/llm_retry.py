"""v1.2: 冷启动降级 (3 级 fallback).

- L1: 主 GLM 调
- L2: 失败 → 1 次重试 (exponential backoff 500ms/1s)
- L3: 仍失败 → FULLTEXT 检索 + 模板答案 (degraded=True)
- L4: FULLTEXT 也失败 → 503 友好文案

设计目标: GLM key 未配 / 配额耗尽 / 网络抖动 → 端点仍返 200, 不挂用户.
"""
import logging
import time
from typing import Any, Iterator

from app.config import settings

logger = logging.getLogger(__name__)


def llm_call_with_retry(
    system: str,
    user: str,
    stream: bool = False,
) -> tuple[bool, str | Iterator[str] | None, str | None]:
    """3 级 fallback 包 GLM 调用.

    返回: (ok, content_or_stream, error_msg)
    - ok=True  → LLM 成功, content_or_stream 是结果
    - ok=False → LLM 失败, content_or_stream=None, error_msg 是原因

    用法 (run_agent):
        ok, content, err = llm_call_with_retry(system, user, stream=False)
        if not ok:
            return degraded_answer(question)  # 降级
    """
    # L1: 主 GLM 调
    from app.llm.glm import glm_client

    if not settings.glm_api_key:
        logger.warning("GLM_API_KEY 未配置, 走降级路径")
        return False, None, "GLM_API_KEY 未配置"

    try:
        if stream:
            return True, glm_client.chat_stream(system, user), None
        else:
            return True, glm_client.chat(system, user), None
    except Exception as e:
        logger.warning("L1 GLM 失败: %s", e)

    # L2: 重试 1 次
    for delay in (0.5, 1.0):
        logger.info("L2 重试 GLM (delay=%ss)", delay)
        time.sleep(delay)
        try:
            if stream:
                return True, glm_client.chat_stream(system, user), None
            else:
                return True, glm_client.chat(system, user), None
        except Exception as e:
            logger.warning("L2 重试失败: %s", e)
            continue

    return False, None, "GLM 调用失败 (L1 + L2 重试均失败)"


def is_retryable_error(err: str | None) -> bool:
    """判断 err 是否可降级 (vs 永久错误)."""
    if not err:
        return False
    if "GLM_API_KEY 未配置" in err:
        return True
    if "401" in err or "403" in err or "配额" in err or "rate limit" in err.lower():
        return True
    if "timeout" in err.lower() or "connection" in err.lower():
        return True
    return False

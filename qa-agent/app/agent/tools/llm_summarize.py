from typing import Any

from app.agent.prompts import EXTRACT_SYSTEM
from app.llm.glm import glm_client


def run(args: dict[str, Any], ctx: dict[str, Any]) -> str:
    task = args.get("task") or "摘要"
    text = (args.get("text") or "")[:12000]
    focus = args.get("focus") or ""
    user = f"任务: {task}\n关注点: {focus}\n\n文本:\n{text}\n\n请用中文给出简洁结果。"
    return glm_client.chat(EXTRACT_SYSTEM, user)

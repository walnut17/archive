"""ask_clarification: 追问用户 (中断 ReAct 循环，等待用户输入)."""

import logging
from typing import Any

logger = logging.getLogger(__name__)


def run(args: dict[str, Any], ctx: dict[str, Any]) -> dict[str, Any]:
    question = args.get("question", "请补充详细信息")
    options = args.get("options", [])

    ctx["interrupted"] = True

    return {
        "clarification": True,
        "question": question,
        "options": options,
    }

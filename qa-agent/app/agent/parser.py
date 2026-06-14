import json
import re
from typing import Any

from app.agent.prompts import DEFAULT_REJECT_ANSWER


FINAL_ANSWER = "FINAL_ANSWER"


def extract_json_block(text: str) -> str | None:
    if not text:
        return None
    text = text.strip()
    if text.startswith("{") and text.endswith("}"):
        return text
    fence = re.search(r"```(?:json)?\s*([\s\S]*?)```", text, re.IGNORECASE)
    if fence:
        return fence.group(1).strip()
    start = text.find("{")
    end = text.rfind("}")
    if start >= 0 and end > start:
        return text[start : end + 1]
    return None


def parse_agent_step(raw: str, iteration: int) -> dict[str, Any]:
    """Parse LLM output into agent step dict."""
    default_reject = {
        "iteration": iteration,
        "thought": "输出格式不符合 JSON 规范",
        "tool": FINAL_ANSWER,
        "toolArgs": json.dumps(
            {
                "answer": DEFAULT_REJECT_ANSWER
            },
            ensure_ascii=False,
        ),
        "observation": "",
    }
    if not raw or not raw.strip():
        default_reject["thought"] = "LLM 返回空"
        default_reject["toolArgs"] = json.dumps({"answer": "LLM 未返回有效响应"}, ensure_ascii=False)
        return default_reject

    block = extract_json_block(raw)
    if not block:
        # 离题或非 JSON：若像拒答文案则直接用，否则礼貌拒答
        if any(k in raw for k in ("档案", "项目", "投委会", "无法回答", "不能回答")):
            return {
                "iteration": iteration,
                "thought": "直接文本答复",
                "tool": FINAL_ANSWER,
                "toolArgs": json.dumps({"answer": raw.strip()}, ensure_ascii=False),
                "observation": "",
            }
        return default_reject

    try:
        node = json.loads(block)
    except json.JSONDecodeError:
        return default_reject

    tool = str(node.get("tool") or FINAL_ANSWER)
    args = node.get("args") or {}
    if isinstance(args, str):
        tool_args = args
    else:
        tool_args = json.dumps(args, ensure_ascii=False)

    return {
        "iteration": iteration,
        "thought": str(node.get("thought") or ""),
        "tool": tool,
        "toolArgs": tool_args,
        "observation": "",
    }


def final_answer_from_args(tool_args: str) -> str:
    try:
        args = json.loads(tool_args or "{}")
    except json.JSONDecodeError:
        return tool_args or "无法生成答案"
    if isinstance(args, dict) and "answer" in args:
        return str(args["answer"])
    return tool_args or "无法生成答案"

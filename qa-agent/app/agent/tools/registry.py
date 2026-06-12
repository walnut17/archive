import json
import logging
from typing import Any

from app.agent.tools import find_project, llm_summarize, query_mysql, search_fulltext

logger = logging.getLogger(__name__)

_TOOLS = {
    "find_project": find_project.run,
    "search_fulltext": search_fulltext.run,
    "query_mysql": query_mysql.run,
    "llm_summarize": llm_summarize.run,
}


def dispatch_tool(name: str, args_json: str, ctx: dict[str, Any]) -> Any:
    fn = _TOOLS.get(name)
    if not fn:
        raise ValueError(f"未知工具: {name}")
    try:
        args = json.loads(args_json or "{}")
    except json.JSONDecodeError as e:
        raise ValueError(f"工具参数 JSON 无效: {e}") from e
    result = fn(args, ctx)
    if name == "find_project" and isinstance(result, list) and result:
        top = result[0]
        if isinstance(top, dict) and top.get("code"):
            ctx["project_code"] = top["code"]
    return result

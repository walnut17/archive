import json
import re
from typing import Any

from app.agent.prompts import EXTRACT_SYSTEM, EXTRACT_USER_TEMPLATE
from app.db.connection import db_cursor
from app.llm.glm import glm_client


def extract_project_fields(material_version_id: int) -> dict[str, Any]:
    with db_cursor() as cur:
        cur.execute(
            """
            SELECT id, original_filename, parsed_text, parse_status
            FROM material_version WHERE id = %s
            """,
            (material_version_id,),
        )
        row = cur.fetchone()

    if not row:
        return {
            "success": False,
            "failure_type": "FIELD_MISSING",
            "message": f"材料版本不存在: {material_version_id}",
            "data": None,
        }

    text = row.get("parsed_text") or ""
    title = row.get("original_filename") or ""
    if not text.strip():
        return {
            "success": False,
            "failure_type": "PARSE_ERROR",
            "message": "材料尚未解析完成，请稍后重试",
            "retryable": True,
            "data": None,
        }

    user = EXTRACT_USER_TEMPLATE.format(
        title=title, content=text[:15000], max_chars=15000
    )
    try:
        raw = glm_client.chat(EXTRACT_SYSTEM, user)
    except Exception as e:
        return {
            "success": False,
            "failure_type": "API_ERROR",
            "message": str(e),
            "retryable": True,
            "data": None,
        }

    cleaned = raw.strip()
    if cleaned.startswith("```"):
        cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned)
        cleaned = re.sub(r"\s*```\s*$", "", cleaned)

    try:
        data = json.loads(cleaned)
    except json.JSONDecodeError:
        return {
            "success": False,
            "failure_type": "PARSE_ERROR",
            "message": "LLM 返回无法解析为 JSON",
            "retryable": True,
            "data": None,
        }

    return {"success": True, "data": data, "failure_type": None, "message": None}

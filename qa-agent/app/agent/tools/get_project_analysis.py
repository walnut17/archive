"""读取后台深度分析快照 — 项目层/资产层关键信息."""

from __future__ import annotations

import json
from typing import Any

from app.analysis.context import load_project_context_by_code
from app.analysis.repository import get_analysis_state, list_snapshots
from app.db.connection import db_cursor


def get_project_analysis(args: dict[str, Any], ctx: dict[str, Any]) -> dict[str, Any]:
    project_code = (args.get("projectCode") or ctx.get("project_code") or "").strip()
    template_code = (args.get("templateCode") or "").strip() or None
    if not project_code:
        return {"error": "projectCode 必填"}

    pctx = load_project_context_by_code(project_code)
    if not pctx:
        return {"error": f"项目不存在或无 parsed 材料: {project_code}"}

    state = get_analysis_state(pctx.project_id) or {}
    snapshots = list_snapshots(pctx.project_id, template_code=template_code)

    facts: list[dict[str, Any]] = []
    try:
        with db_cursor() as cur:
            cur.execute(
                """
                SELECT fact_type, fact_value, confidence_level, evidence_snippet
                FROM project_fact
                WHERE project_id = %s AND status = 'active'
                ORDER BY fact_type
                """,
                (pctx.project_id,),
            )
            facts = cur.fetchall() or []
    except Exception:
        pass

    assets: list[dict[str, Any]] = []
    try:
        with db_cursor() as cur:
            cur.execute(
                """
                SELECT id, asset_type, name, display_name, metadata_json
                FROM project_asset
                WHERE project_id = %s AND status = 'active'
                ORDER BY asset_type, name
                """,
                (pctx.project_id,),
            )
            assets = cur.fetchall() or []
            for a in assets:
                meta = a.get("metadata_json")
                if isinstance(meta, str):
                    try:
                        a["metadata_json"] = json.loads(meta)
                    except json.JSONDecodeError:
                        pass
    except Exception:
        pass

    compact_snaps = []
    for s in snapshots:
        compact_snaps.append(
            {
                "templateCode": s.get("template_code"),
                "scope": s.get("scope"),
                "assetId": s.get("asset_id"),
                "summary": s.get("summary_text"),
                "result": s.get("result_json"),
                "confidenceLevel": s.get("confidence_level"),
                "updatedAt": str(s.get("updated_at") or ""),
            }
        )

    return {
        "projectCode": pctx.project_code,
        "projectName": pctx.project_name,
        "materialFingerprint": pctx.material_fingerprint,
        "analysisState": {
            "lastStatus": state.get("last_status"),
            "lastCompletedAt": str(state.get("last_completed_at") or ""),
            "snapshotCount": state.get("snapshot_count"),
            "assetCount": state.get("asset_count"),
            "lastError": state.get("last_error"),
        },
        "snapshots": compact_snaps,
        "facts": facts,
        "assets": assets,
    }


run = get_project_analysis

"""分析结果映射到 project_fact / project_asset / timepoint."""

from __future__ import annotations

import json
import logging
from typing import Any

from app.analysis.models import AnalysisTemplate, ProjectContext
from app.db.connection import db_cursor

logger = logging.getLogger(__name__)


def upsert_project_assets_from_inventory(
    project_id: int,
    inventory: dict[str, Any],
    *,
    source_template: str = "project.credit_inventory",
) -> list[int]:
    credits = inventory.get("credits") or []
    if not isinstance(credits, list):
        return []

    asset_ids: list[int] = []
    with db_cursor() as cur:
        for item in credits:
            if isinstance(item, str):
                name = item.strip()
                meta = None
            elif isinstance(item, dict):
                name = str(item.get("name") or "").strip()
                meta = {k: v for k, v in item.items() if k != "name"}
            else:
                continue
            if not name:
                continue
            cur.execute(
                """
                SELECT id FROM project_asset
                WHERE project_id = %s AND asset_type = 'credit' AND name = %s
                LIMIT 1
                """,
                (project_id, name),
            )
            row = cur.fetchone()
            if row:
                asset_ids.append(int(row["id"]))
                continue
            try:
                cur.execute(
                    """
                    INSERT INTO project_asset
                        (project_id, asset_type, name, display_name, source_template, metadata_json)
                    VALUES (%s, 'credit', %s, %s, %s, %s)
                    """,
                    (
                        project_id,
                        name,
                        name,
                        source_template,
                        json.dumps(meta, ensure_ascii=False) if meta else None,
                    ),
                )
                asset_ids.append(int(cur.lastrowid))
            except Exception as e:
                logger.debug("project_asset 写入跳过: %s", e)
    return asset_ids


def sync_facts_from_snapshots(project_id: int) -> int:
    """将部分快照同步为 project_fact（利率、交易形式等）."""
    count = 0
    try:
        with db_cursor() as cur:
            cur.execute(
                """
                SELECT template_code, result_json, summary_text, confidence, confidence_level
                FROM analysis_snapshot
                WHERE project_id = %s AND asset_id IS NULL
                """,
                (project_id,),
            )
            rows = cur.fetchall() or []
            for row in rows:
                code = row.get("template_code") or ""
                payload = row.get("result_json")
                if isinstance(payload, str):
                    payload = json.loads(payload)
                if not isinstance(payload, dict):
                    continue

                mappings: list[tuple[str, str]] = []
                if code == "project.interest_rate_schedule":
                    rate = payload.get("currentRate")
                    if rate is not None:
                        mappings.append(("interest_rate", f"{rate}{payload.get('rateUnit') or ''}"))
                    sched = payload.get("rateSchedule")
                    if sched:
                        mappings.append(("interest_rate_schedule", json.dumps(sched, ensure_ascii=False)))
                elif code == "project.investment_structure":
                    for key, fact_type in (
                        ("transactionForm", "transaction_form"),
                        ("investmentStructure", "investment_structure"),
                        ("coreAssets", "core_assets"),
                    ):
                        val = payload.get(key)
                        if val:
                            mappings.append((fact_type, str(val)[:4000]))

                for fact_type, fact_value in mappings:
                    cur.execute(
                        """
                        SELECT id FROM project_fact
                        WHERE project_id = %s AND fact_type = %s AND status = 'active'
                        LIMIT 1
                        """,
                        (project_id, fact_type),
                    )
                    existing = cur.fetchone()
                    conf = row.get("confidence")
                    level = row.get("confidence_level") or "AI_INFERRED"
                    snippet = (row.get("summary_text") or "")[:2000]
                    if existing:
                        cur.execute(
                            """
                            UPDATE project_fact
                            SET fact_value = %s, confidence = %s, confidence_level = %s,
                                evidence_snippet = %s, updated_at = CURRENT_TIMESTAMP
                            WHERE id = %s
                            """,
                            (fact_value, conf, level, snippet, existing["id"]),
                        )
                    else:
                        cur.execute(
                            """
                            INSERT INTO project_fact
                                (project_id, fact_type, fact_value, confidence,
                                 confidence_level, evidence_snippet, status)
                            VALUES (%s, %s, %s, %s, %s, %s, 'active')
                            """,
                            (project_id, fact_type, fact_value, conf, level, snippet),
                        )
                    count += 1
    except Exception as e:
        logger.warning("sync_facts_from_snapshots 失败: %s", e)
    return count


def write_timepoints(project_id: int) -> int:
    """从 analysis_snapshot 提取时点写入 timepoint 表 (替代 Java TimepointExtractor)."""
    count = 0
    with db_cursor() as cur:
        cur.execute(
            "SELECT summary FROM analysis_snapshot WHERE project_id = %s AND summary IS NOT NULL",
            (project_id,),
        )
        rows = cur.fetchall() or []
        for row in rows:
            raw = row["summary"] or ""
            for line in raw.split("\n"):
                line = line.strip()
                if not line or len(line) < 12:
                    continue
                if line[4] == "-" and line[7] == "-":
                    parts = line.split(":", 1)
                    if len(parts) != 2:
                        continue
                    event_date = parts[0].strip()
                    title = parts[1].strip()
                    cur.execute(
                        """INSERT IGNORE INTO timepoint
                           (project_id, title, event_date, source, status)
                           VALUES (%s, %s, %s, 'ANALYSIS', 'CONFIRMED')""",
                        (project_id, title[:255], event_date),
                    )
                    count += cur.rowcount
    logger.info("write_timepoints: project=%d, written=%d", project_id, count)
    return count

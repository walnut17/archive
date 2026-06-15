"""get_project_business_data unit tests."""

from __future__ import annotations

from unittest.mock import MagicMock, patch

from app.agent.tools import get_project_business_data


def test_get_project_business_data_includes_proposal_fields():
    with patch("app.agent.tools.get_project_business_data.db_cursor") as db_mock:
        cur = MagicMock()
        cur.fetchone.return_value = {
            "id": 1,
            "code": "shtx26007",
            "name": "lmz授信",
            "status": "通过",
            "amount_wan": 300,
            "customer_name": None,
            "category": None,
            "todo_count": 0,
            "material_count": 2,
            "committee_count": 1,
            "maintenance_count": 0,
        }
        cur.fetchall.return_value = [
            {
                "code": "shtx26007",
                "title": "投资申请报告",
                "type": "申请",
                "status": "通过",
            }
        ]
        cm = MagicMock()
        cm.__enter__.return_value = cur
        cm.__exit__.return_value = False
        db_mock.return_value = cm

        out = get_project_business_data.run({"projectCode": "shtx26007"}, {})

    assert out["proposalCount"] == 1  # deprecated alias
    assert out["committeeProposalCount"] == 1
    assert out["maintenanceBundleCount"] == 0
    assert out["proposals"] is not None
    assert out["materialCount"] == 2
    assert out["proposals"][0]["code"] == "shtx26007"

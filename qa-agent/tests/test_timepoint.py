"""write_timepoints 单测."""

from unittest.mock import MagicMock, patch

from app.analysis.mapper import write_timepoints


def test_write_timepoints_skips_non_date_lines():
    with patch("app.analysis.mapper.db_cursor") as db_mock:
        cur = MagicMock()
        cur.fetchall.return_value = [
            {"summary": "项目投资结构说明\n2026-06-15: 完成尽调报告\n风险较低"},
        ]
        cur.rowcount = 1
        cm = MagicMock()
        cm.__enter__.return_value = cur
        cm.__exit__.return_value = False
        db_mock.return_value = cm

        count = write_timepoints(1)

        assert count >= 1


def test_write_timepoints_empty_summary():
    with patch("app.analysis.mapper.db_cursor") as db_mock:
        cur = MagicMock()
        cur.fetchall.return_value = []
        cm = MagicMock()
        cm.__enter__.return_value = cur
        cm.__exit__.return_value = False
        db_mock.return_value = cm

        count = write_timepoints(1)

        assert count == 0

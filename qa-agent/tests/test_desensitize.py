"""Tests for LLM desensitization."""

from __future__ import annotations

from app.services.desensitize import StreamUnmasker, mask_text, unmask_text


def test_mask_org_name_lingdou():
    text = "目标债权为南安市岭兜建材二厂债权，融资300万元。"
    result = mask_text(text)
    assert "岭兜建材二厂" not in result.text
    assert "300万元" not in result.text or "[AMT_" in result.text
    assert result.masked_count >= 2
    restored = unmask_text(result.text, result.mapping)
    assert "岭兜建材二厂" in restored
    assert "300万元" in restored


def test_mask_id_card():
    text = "身份证号 350582199001011234 用于核验。"
    result = mask_text(text)
    assert "350582199001011234" not in result.text
    assert "[ID_" in result.text


def test_unmask_stream_partial_token():
    mapping = {"[ORG_001]": "岭兜建材二厂"}
    u = StreamUnmasker(mapping)
    assert u.feed("标的为[ORG") == "标的为"
    assert u.feed("_001]。") == "岭兜建材二厂。"


def test_roundtrip_preserves_meaning():
    original = "泉州合作方申请以南安市岭兜建材二厂债权做回购，本金545万元。"
    masked = mask_text(original)
    back = unmask_text(masked.text, masked.mapping)
    assert "岭兜建材二厂" in back
    assert "545万元" in back

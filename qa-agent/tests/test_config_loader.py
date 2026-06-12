import json
from pathlib import Path

import pytest

from app.config_loader import _flatten, resolve_config_path


def test_flatten_skips_comment_keys():
    data = {"glm": {"_comment": "x", "apiKey": "k1"}, "database": {"host": "localhost", "port": 3306}}
    flat = _flatten(data)
    assert flat["glm.apiKey"] == "k1"
    assert flat["database.host"] == "localhost"
    assert flat["database.port"] == 3306


def test_resolve_config_path_from_relative_config(tmp_path, monkeypatch):
    monkeypatch.delenv("CONFIG_JSON_PATH", raising=False)
    cfg = tmp_path / "config" / "config.json"
    cfg.parent.mkdir(parents=True)
    cfg.write_text('{"glm":{"apiKey":"test"}}', encoding="utf-8")
    monkeypatch.chdir(tmp_path)
    found = resolve_config_path()
    assert found is not None
    assert found.name == "config.json"


def test_settings_loads_glm_from_json(tmp_path, monkeypatch):
    monkeypatch.setenv("CONFIG_JSON_PATH", str(tmp_path / "c.json"))
    cfg = tmp_path / "c.json"
    cfg.write_text(
        json.dumps(
            {
                "glm": {"apiKey": "key-from-json", "chatModel": "glm-4-flash"},
                "database": {"host": "dbhost", "port": 3307, "username": "u", "password": "p", "database": "d"},
                "qaAgent": {"port": 9001},
            }
        ),
        encoding="utf-8",
    )
    from app.config import Settings

    s = Settings.load()
    assert s.glm_api_key == "key-from-json"
    assert s.mysql_host == "dbhost"
    assert s.qa_agent_port == 9001

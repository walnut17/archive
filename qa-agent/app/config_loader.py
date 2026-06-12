"""Load shared config.json — same lookup order as Java ConfigJsonLoader."""

from __future__ import annotations

import json
import logging
import os
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)


def resolve_config_path() -> Path | None:
    """Mirror backend ConfigJsonLoader.resolveConfigPath() order."""
    env_path = os.environ.get("CONFIG_JSON_PATH", "").strip()
    if env_path:
        p = Path(env_path)
        if p.is_file():
            return p
        logger.warning("CONFIG_JSON_PATH=%s not found", env_path)

    candidates = [
        Path("D:/archive/config/config.json"),
        Path("config/config.json"),
        Path("../config/config.json"),
        # repo dev: qa-agent/ → projects-online/config/
        Path(__file__).resolve().parents[2] / "config" / "config.json",
    ]
    for p in candidates:
        if p.is_file():
            return p
    return None


def _flatten(obj: Any, prefix: str = "") -> dict[str, Any]:
    """Flatten nested JSON; skip keys starting with _ (comments)."""
    out: dict[str, Any] = {}
    if isinstance(obj, dict):
        for key, value in obj.items():
            if str(key).startswith("_"):
                continue
            out.update(_flatten(value, f"{prefix}{key}."))
    elif isinstance(obj, list):
        out[prefix[:-1]] = json.dumps(obj, ensure_ascii=False)
    elif prefix:
        out[prefix[:-1]] = obj
    return out


def load_flat_config() -> dict[str, Any]:
    path = resolve_config_path()
    if path is None:
        logger.info("config.json not found; using defaults / env overrides only")
        return {}

    logger.info("Loading config.json: %s", path.resolve())
    raw = path.read_text(encoding="utf-8")
    if raw.startswith("\ufeff"):
        raw = raw[1:]
    data = json.loads(raw)
    return _flatten(data)


def get_str(flat: dict[str, Any], key: str, default: str = "") -> str:
    val = flat.get(key, default)
    return str(val) if val is not None else default


def get_int(flat: dict[str, Any], key: str, default: int) -> int:
    val = flat.get(key)
    if val is None:
        return default
    try:
        return int(val)
    except (TypeError, ValueError):
        return default


def get_float(flat: dict[str, Any], key: str, default: float) -> float:
    val = flat.get(key)
    if val is None:
        return default
    try:
        return float(val)
    except (TypeError, ValueError):
        return default

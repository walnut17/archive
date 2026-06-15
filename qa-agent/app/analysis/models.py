"""分析框架常量与数据模型."""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from typing import Any

ANALYZER_VERSION = "v1"
FEATURE_ANALYSIS_WORKER = "2026-06-15"
FEATURE_LLM_DESENSITIZE = "2026-06-15"
FEATURE_DEPLOY_SANDBOX = "2026-06-15"


class AnalysisScope(StrEnum):
    PROJECT = "project"
    ASSET = "asset"
    MATERIAL = "material"


class JobStatus(StrEnum):
    PENDING = "pending"
    RUNNING = "running"
    SUCCESS = "success"
    FAILED = "failed"
    CANCELLED = "cancelled"


class JobType(StrEnum):
    PROJECT_DEEP = "project_deep"
    ASSET_CREDIT = "asset_credit"


@dataclass
class AnalysisTemplate:
    code: str
    name: str
    scope: str
    prompt_template: str
    output_schema: dict[str, Any]
    description: str = ""
    max_input_chars: int = 30000
    enabled: bool = True
    builtin: bool = False
    sort_order: int = 0


@dataclass
class ProjectContext:
    project_id: int
    project_code: str
    project_name: str
    materials_text: str
    material_fingerprint: str
    material_version_ids: list[int]


@dataclass
class ExtractResult:
    success: bool
    data: dict[str, Any] | None
    raw_text: str = ""
    message: str | None = None
    confidence: float | None = None
    confidence_level: str = "AI_INFERRED"

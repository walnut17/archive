from pydantic import BaseModel, Field


class AskRequest(BaseModel):
    question: str = Field(min_length=1, max_length=4000)
    session_id: str | None = None


class TurnRequest(BaseModel):
    question: str = Field(min_length=1, max_length=4000)


class AgentStep(BaseModel):
    iteration: int
    thought: str = ""
    tool: str = ""
    toolArgs: str = ""
    observation: str = ""


class AskResponse(BaseModel):
    answer: str
    agent_mode: bool = True
    steps: list[AgentStep] = []
    tool_calls: int = 0
    project_switch_hint: str | None = None
    confidence_badge: str | None = None
    agent_sources: list[dict] = []


class ExtractRequest(BaseModel):
    material_version_id: int


class ExtractResponse(BaseModel):
    success: bool
    data: dict | None = None
    failure_type: str | None = None
    message: str | None = None
    retryable: bool | None = None


class AnalysisEnqueueRequest(BaseModel):
    project_id: int | None = None
    project_code: str | None = None
    reason: str | None = "manual"


class AnalysisEnqueueResponse(BaseModel):
    job_id: int
    project_id: int


class AnalysisRunOnceResponse(BaseModel):
    discovered: int
    processed: bool
    queue: dict = {}


class AnalysisStatusResponse(BaseModel):
    enabled: bool
    alive: bool = False
    running_job_id: int | None = None
    jobs_processed: int = 0
    last_tick_at: float | None = None
    last_error: str | None = None
    queue: dict = {}
    tables_ready: bool = False
    templates: list[dict] = []


class ProjectAnalysisResponse(BaseModel):
    project_id: int
    project_code: str
    project_name: str
    material_fingerprint: str
    analysis_state: dict = {}
    snapshots: list[dict] = []
    queue: dict = {}

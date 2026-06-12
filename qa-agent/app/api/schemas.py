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

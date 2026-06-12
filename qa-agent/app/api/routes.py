from fastapi import APIRouter, HTTPException

from app.agent.engine import run_agent
from app.api.schemas import (
    AskRequest,
    AskResponse,
    AgentStep,
    ExtractRequest,
    ExtractResponse,
    TurnRequest,
)
from app.services.extract import extract_project_fields

router = APIRouter()


@router.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "qa-agent"}


@router.post("/v1/ask", response_model=AskResponse)
def ask(req: AskRequest) -> AskResponse:
    result = run_agent(req.question, req.session_id)
    return _to_response(result)


@router.post("/v1/turn/{session_id}", response_model=AskResponse)
def turn(session_id: str, req: TurnRequest) -> AskResponse:
    if not session_id.strip():
        raise HTTPException(status_code=400, detail="session_id 不能为空")
    result = run_agent(req.question, session_id)
    return _to_response(result)


@router.post("/v1/extract/project-fields", response_model=ExtractResponse)
def extract(req: ExtractRequest) -> ExtractResponse:
    result = extract_project_fields(req.material_version_id)
    return ExtractResponse(**result)


def _to_response(result: dict) -> AskResponse:
    steps = [
        AgentStep(
            iteration=s.get("iteration", 0),
            thought=s.get("thought") or "",
            tool=s.get("tool") or "",
            toolArgs=s.get("toolArgs") or "",
            observation=s.get("observation") or "",
        )
        for s in result.get("steps") or []
    ]
    return AskResponse(
        answer=result.get("answer") or "",
        agent_mode=result.get("agent_mode", True),
        steps=steps,
        tool_calls=result.get("tool_calls") or len(steps),
        project_switch_hint=result.get("project_switch_hint"),
        confidence_badge=result.get("confidence_badge"),
        agent_sources=result.get("agent_sources") or [],
    )

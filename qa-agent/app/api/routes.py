from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse

from app.agent.engine import run_agent, run_agent_stream
from app.api.schemas import (
    AskRequest,
    AskResponse,
    AgentStep,
    ExtractRequest,
    ExtractResponse,
    TurnRequest,
)
from app.config import settings
from app.services.extract import extract_project_fields
from app.services.version_info import get_runtime_version

router = APIRouter()


@router.get("/health")
def health() -> dict:
    runtime = get_runtime_version()
    return {
        "status": "ok",
        **runtime,
        "config_json": settings.config_json_path or None,
        "deploy_enabled": bool(settings.qa_agent_deploy_token.strip()),
    }


@router.post("/v1/ask", response_model=AskResponse)
def ask(req: AskRequest) -> AskResponse:
    """非流式单轮问答 (向后兼容)."""
    result = run_agent(req.question, req.session_id)
    return _to_response(result)


@router.post("/v1/ask/stream")
def ask_stream(req: AskRequest):
    """v1.2 流式单轮问答 (SSE).

    事件类型 4 种:
    - step: ReAct 步完成
    - token: LLM 生成的 token
    - source: 来源命中 (PROJECT/MATERIAL/...)
    - done: 结束 (含 answer/tool_calls/agent_sources/...)
    """
    return StreamingResponse(
        run_agent_stream(req.question, req.session_id),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",  # 禁用 nginx 缓冲
            "Connection": "keep-alive",
        },
    )


@router.post("/v1/turn/{session_id}", response_model=AskResponse)
def turn(session_id: str, req: TurnRequest) -> AskResponse:
    """非流式多轮问答 (向后兼容)."""
    if not session_id.strip():
        raise HTTPException(status_code=400, detail="session_id 不能为空")
    result = run_agent(req.question, session_id)
    return _to_response(result)


@router.post("/v1/turn/{session_id}/stream")
def turn_stream(session_id: str, req: TurnRequest):
    """v1.2 流式多轮问答 (SSE) + 项目锁穿透."""
    if not session_id.strip():
        raise HTTPException(status_code=400, detail="session_id 不能为空")
    return StreamingResponse(
        run_agent_stream(req.question, session_id),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
            "Connection": "keep-alive",
        },
    )


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

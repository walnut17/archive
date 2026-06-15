import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.analysis.runtime import get_analysis_worker
from app.api.analysis import router as analysis_router
from app.api.deploy import router as deploy_router
from app.api.routes import router
from app.config import settings
from app.services.self_update import read_version

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
_log = logging.getLogger(__name__)

_analysis_worker = get_analysis_worker()


@asynccontextmanager
async def lifespan(app: FastAPI):
    _analysis_worker.start()
    yield
    _analysis_worker.stop()


if settings.config_json_path:
    _log.info("qa-agent config: %s", settings.config_json_path)
else:
    _log.warning("config.json not found — using defaults; set CONFIG_JSON_PATH or copy config/config.example.json")

app = FastAPI(title="Archive QA Agent", version=read_version(), lifespan=lifespan)
app.include_router(router)
app.include_router(deploy_router)
app.include_router(analysis_router)

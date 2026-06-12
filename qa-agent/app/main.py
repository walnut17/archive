import logging

from fastapi import FastAPI

from app.api.routes import router
from app.config import settings

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
_log = logging.getLogger(__name__)
if settings.config_json_path:
    _log.info("qa-agent config: %s", settings.config_json_path)
else:
    _log.warning("config.json not found — using defaults; set CONFIG_JSON_PATH or copy config/config.example.json")

app = FastAPI(title="Archive QA Agent", version="1.0.0")
app.include_router(router)

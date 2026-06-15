#!/usr/bin/env python3
"""独立后台分析 worker（不启动 HTTP 时可单独运行）."""

from __future__ import annotations

import logging
import signal
import sys
import time

from app.analysis.runtime import get_analysis_worker
from app.config import settings

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger(__name__)


def main() -> int:
    worker = get_analysis_worker()
    if not settings.analysis_worker_enabled:
        logger.error("analysis worker 未启用")
        return 1

    stop = False

    def _handle(sig, frame):
        nonlocal stop
        logger.info("收到信号 %s，准备退出", sig)
        stop = True

    signal.signal(signal.SIGINT, _handle)
    signal.signal(signal.SIGTERM, _handle)

    worker.start()
    logger.info("独立 analysis worker 运行中，poll=%ss", settings.analysis_worker_poll_seconds)
    try:
        while not stop:
            time.sleep(1)
    finally:
        worker.stop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

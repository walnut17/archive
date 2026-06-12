"""network_dict_lookup: 网络字典查术语 (百度百科 / 维基百科)."""

import logging
import urllib.parse
import urllib.request
import json
from typing import Any

logger = logging.getLogger(__name__)

TIMEOUT = 5  # 每源超时


def _fetch_baidu(query: str) -> dict[str, Any] | None:
    """搜索百度百科."""
    try:
        url = f"https://baike.baidu.com/item/{urllib.parse.quote(query)}"
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
            html = resp.read().decode("utf-8", errors="replace")
            # 简单提取：找 <meta name="description"> 或标题
            import re
            m = re.search(r'<meta\s+name="description"\s+content="([^"]+)"', html)
            if m:
                return {"found": True, "definition": m.group(1)[:500], "source": "baidu_baike"}
    except Exception as e:
        logger.debug("baidu lookup failed: %s", e)
    return None


def _fetch_wiki(query: str) -> dict[str, Any] | None:
    """搜索维基百科 (中文)."""
    try:
        api_url = ("https://zh.wikipedia.org/w/api.php"
                   "?action=query&format=json&prop=extracts&exintro=1&explaintext=1"
                   f"&titles={urllib.parse.quote(query)}")
        req = urllib.request.Request(api_url, headers={"User-Agent": "ArchiveQA/1.0"})
        with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            pages = data.get("query", {}).get("pages", {})
            for page_id, page in pages.items():
                if page_id != "-1" and page.get("extract"):
                    return {
                        "found": True,
                        "definition": page["extract"][:500],
                        "source": "wikipedia_zh",
                    }
    except Exception as e:
        logger.debug("wiki lookup failed: %s", e)
    return None


_SOURCES = [
    ("baidu_baike", _fetch_baidu),
    ("wikipedia_zh", _fetch_wiki),
]


def run(args: dict[str, Any], ctx: dict[str, Any]) -> dict[str, Any]:
    query = args.get("query", "").strip()
    if not query:
        return {"found": False, "reason": "EMPTY_QUERY"}

    for source_name, fetcher in _SOURCES:
        result = fetcher(query)
        if result and result.get("found"):
            return result

    return {"found": False, "reason": "INTRANET_BLOCKED", "source": None}

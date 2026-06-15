"""LLM 调用前脱敏 — 姓名/机构名、证件号、金额."""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from typing import Callable

# 机构/企业/工厂名称
_ORG_PATTERNS: list[re.Pattern[str]] = [
    re.compile(
        r"[\u4e00-\u9fff]{2,40}"
        r"(?:有限责任公司|股份有限公司|有限公司|集团公司|集团|农商行|商业银行|"
        r"信用社|合作社|事务所|研究院|管理中心|经营部|商行)"
    ),
    re.compile(r"[\u4e00-\u9fff]{2,30}(?:建材|建设|投资|置业|贸易)[\u4e00-\u9fff]{0,12}厂"),
    re.compile(r"[\u4e00-\u9fff]{2,12}建材[\u4e00-\u9fff]{0,8}二厂"),
    re.compile(r"(?:福建省|福建|南安市|南安|泉州市|泉州)[\u4e00-\u9fff]{2,28}(?:厂|公司|债权|商行)"),
    re.compile(r"[\u4e00-\u9fff]{2,28}债权"),
]

# 人名（关键字引导，降低误伤）
_PERSON_PATTERNS: list[re.Pattern[str]] = [
    re.compile(
        r"(?:(?:债务人|担保人|法定代表人|申请人|联系人|负责人|合作方|债权人|保证人)"
        r"[为是：:\s]*)([\u4e00-\u9fff]{2,4})"
    ),
    re.compile(r"([\u4e00-\u9fff]{2,3})(?=申请(?:以|对|用))"),
]

# 证件号
_ID_PATTERNS: list[re.Pattern[str]] = [
    re.compile(r"\b[1-9]\d{5}(?:19|20)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[\dXx]\b"),
    re.compile(r"\b\d{15}\b"),
    re.compile(r"\b[0-9A-HJ-NPQRTUWXY]{2}\d{6}[0-9A-HJ-NPQRTUWXY]{10}\b"),
    re.compile(r"\b1[3-9]\d{9}\b"),
]

# 金额
_AMT_PATTERNS: list[re.Pattern[str]] = [
    re.compile(r"\d{1,3}(?:,\d{3})+(?:\.\d+)?\s*(?:万|亿)?元"),
    re.compile(r"\d+(?:\.\d+)?\s*(?:万|亿)元"),
    re.compile(r"\d+(?:\.\d+)?\s*万元"),
    re.compile(r"(?:本金|金额|贷款|融资|投资|回购价|转让价)[为是：:\s]*\d+(?:\.\d+)?\s*(?:万|亿)?元?"),
    re.compile(r"\d+(?:\.\d+)?\s*万(?:元)?(?:贷款|融资|本金)?"),
]


@dataclass
class MaskResult:
    text: str
    mapping: dict[str, str] = field(default_factory=dict)
    masked_count: int = 0


@dataclass
class _Span:
    start: int
    end: int
    kind: str
    original: str


def _collect_spans(text: str, pattern: re.Pattern[str], kind: str, group: int = 0) -> list[_Span]:
    spans: list[_Span] = []
    for m in pattern.finditer(text):
        if group and m.lastindex:
            orig = m.group(group)
            start = m.start(group)
            end = m.end(group)
        else:
            orig = m.group(0)
            start, end = m.start(), m.end()
        if orig and len(orig.strip()) >= 2:
            spans.append(_Span(start, end, kind, orig))
    return spans


def _merge_spans(spans: list[_Span]) -> list[_Span]:
    if not spans:
        return []
    spans.sort(key=lambda s: (s.start, -(s.end - s.start)))
    merged: list[_Span] = []
    for sp in spans:
        if merged and sp.start < merged[-1].end:
            prev = merged[-1]
            if (sp.end - sp.start) > (prev.end - prev.start):
                merged[-1] = sp
            continue
        merged.append(sp)
    return merged


def _apply_spans(text: str, spans: list[_Span]) -> MaskResult:
    counters = {"ORG": 0, "PERSON": 0, "ID": 0, "AMT": 0}
    mapping: dict[str, str] = {}
    out: list[str] = []
    cursor = 0
    masked_count = 0

    for sp in spans:
        if sp.start < cursor:
            continue
        out.append(text[cursor:sp.start])
        counters[sp.kind] = counters.get(sp.kind, 0) + 1
        token = f"[{sp.kind}_{counters[sp.kind]:03d}]"
        mapping[token] = sp.original
        out.append(token)
        cursor = sp.end
        masked_count += 1
    out.append(text[cursor:])
    return MaskResult("".join(out), mapping=mapping, masked_count=masked_count)


def mask_text(text: str) -> MaskResult:
    """对文本脱敏，返回占位符版本与还原映射."""
    if not text:
        return MaskResult(text or "")

    spans: list[_Span] = []
    for pat in _ORG_PATTERNS:
        spans.extend(_collect_spans(text, pat, "ORG"))
    for pat in _PERSON_PATTERNS:
        spans.extend(_collect_spans(text, pat, "PERSON", group=1))
    for pat in _ID_PATTERNS:
        spans.extend(_collect_spans(text, pat, "ID"))
    for pat in _AMT_PATTERNS:
        spans.extend(_collect_spans(text, pat, "AMT"))

    return _apply_spans(text, _merge_spans(spans))


def merge_mappings(*maps: dict[str, str]) -> dict[str, str]:
    out: dict[str, str] = {}
    for m in maps:
        out.update(m)
    return out


def unmask_text(text: str, mapping: dict[str, str]) -> str:
    if not text or not mapping:
        return text or ""
    out = text
    for token in sorted(mapping.keys(), key=len, reverse=True):
        out = out.replace(token, mapping[token])
    return out


_TOKEN_TAIL_RE = re.compile(r"\[(?:ORG|PERSON|ID|AMT)_?\d*$")


class StreamUnmasker:
    """流式输出还原脱敏占位符（处理 token 被截断的情况）."""

    def __init__(self, mapping: dict[str, str]) -> None:
        self.mapping = mapping
        self._hold = ""

    def feed(self, chunk: str) -> str:
        self._hold += chunk
        if "[" in self._hold and _TOKEN_TAIL_RE.search(self._hold):
            cut = self._hold.rfind("[")
            safe = self._hold[:cut]
            self._hold = self._hold[cut:]
            return unmask_text(safe, self.mapping)
        emitted = unmask_text(self._hold, self.mapping)
        self._hold = ""
        return emitted

    def flush(self) -> str:
        if not self._hold:
            return ""
        out = unmask_text(self._hold, self.mapping)
        self._hold = ""
        return out


def mask_llm_messages(system: str, user: str) -> tuple[str, str, dict[str, str]]:
    ms = mask_text(system)
    mu = mask_text(user)
    return ms.text, mu.text, merge_mappings(ms.mapping, mu.mapping)

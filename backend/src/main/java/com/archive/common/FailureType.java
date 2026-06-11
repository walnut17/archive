package com.archive.common;

/**
 * LLM 字段抽取失败类型 (RI-30).
 */
public enum FailureType {
    /** LLM API 4xx/5xx. */
    API_ERROR,
    /** 返回非 JSON. */
    PARSE_ERROR,
    /** 必填字段缺失. */
    FIELD_MISSING,
    /** 字段值异常 (如 amount=-1). */
    VALUE_INVALID,
    /** 调用超时. */
    TIMEOUT
}

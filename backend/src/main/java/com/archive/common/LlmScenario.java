package com.archive.common;

/**
 * LLM 调用场景枚举.
 *
 * <p>埋点时必传,便于按场景聚合.
 *
 * @author Mavis
 */
public enum LlmScenario {
    /** 字段抽取(立项/申请报告). */
    EXTRACTION,
    /** 时点抽取. */
    TIMEPOINT,
    /** 立项-申请对比. */
    COMPARE,
    /** 知识库问答. */
    QA,
    /** 问答重排序. */
    RERANK,
    /** 摘要生成. */
    SUMMARY,
    /** 项目语义匹配 (find_project LLM 兜底: 简称/拼音/错别字 → 全名). */
    PROJECT_MATCH
}

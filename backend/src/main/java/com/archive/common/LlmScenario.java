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
    SUMMARY
}

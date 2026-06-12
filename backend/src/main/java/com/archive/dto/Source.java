package com.archive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 模式来源区条目.
 * 表示一次工具调用命中的业务实体引用，前端按 type 分组渲染.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Source {

    /** 实体类型枚举. */
    public enum SourceType {
        PROJECT,
        MATERIAL,
        TODO,
        TERM
    }

    private SourceType type;
    private String id;
    private String title;
    private String snippet;
}

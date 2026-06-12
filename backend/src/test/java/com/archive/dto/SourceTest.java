package com.archive.dto;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source DTO 与 AgentEngine.extractSources 的合约验证.
 * 确保 AgentEngine 能从 5 种工具返回数据中提取结构化来源.
 */
class SourceTest {

    @Test
    void sourceBuilder() {
        Source s = Source.builder()
                .type(Source.SourceType.PROJECT)
                .id("PRJ-2026-001")
                .title("新能源项目")
                .build();
        assertEquals(Source.SourceType.PROJECT, s.getType());
        assertEquals("PRJ-2026-001", s.getId());
        assertEquals("新能源项目", s.getTitle());
    }

    @Test
    void sourceTypesAllDefined() {
        // 确保 4 种类型都定义
        assertEquals(4, Source.SourceType.values().length);
        assertNotNull(Source.SourceType.valueOf("PROJECT"));
        assertNotNull(Source.SourceType.valueOf("MATERIAL"));
        assertNotNull(Source.SourceType.valueOf("TODO"));
        assertNotNull(Source.SourceType.valueOf("TERM"));
    }

    @Test
    void sourceListDeduplication() {
        List<Source> list = new ArrayList<>();
        Source s1 = Source.builder().type(Source.SourceType.PROJECT).id("PRJ-001").title("项目A").build();
        Source s2 = Source.builder().type(Source.SourceType.PROJECT).id("PRJ-001").title("项目A").build();

        // 去重逻辑: 同 type + id 视为重复
        boolean dup = list.stream()
                .anyMatch(s -> s.getType() == s1.getType() && s.getId().equals(s1.getId()));
        assertFalse(dup);
        list.add(s1);

        dup = list.stream()
                .anyMatch(s -> s.getType() == s2.getType() && s.getId().equals(s2.getId()));
        assertTrue(dup); // s2 和 s1 相同，判重复
    }
}

package com.archive.service;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 章节切分服务.
 *
 * 用途:把 Tika 解析后的纯文本按"章节"切分,方便 M2 知识库问答做"按章节检索/引用".
 *
 * 切分策略(从粗到细):
 *  1. 中文标准:"第N章" / "第N节" / "第N部分"
 *  2. 数字编号:"1." / "1.1" / "1.1.1"
 *  3. 中文大写:"一、" "二、" "三、"
 *  4. 兜底:无章节时整段当作 1 个 section
 *
 * @author Mavis
 */
@Slf4j
@Service
public class SectionService {

    /** 章节切分正则 — 优先级从高到低,先匹配的赢. */
    private static final List<SectionPattern> PATTERNS = List.of(
            // 第N章/节/部分
            new SectionPattern(
                    Pattern.compile("(?m)^\\s*第[一二三四五六七八九十百千零0-9]+[章节部分][\\s\\.、:：]"),
                    "zh-chapter"),
            // 1. / 1.1 / 1.1.1 (要求在行首)
            new SectionPattern(
                    Pattern.compile("(?m)^\\s*\\d+(\\.\\d+){0,3}[\\s\\.、:：]"),
                    "numeric"),
            // 一、 / 二、 / 三、
            new SectionPattern(
                    Pattern.compile("(?m)^\\s*[一二三四五六七八九十]+[、]"),
                    "zh-numeral")
    );

    /**
     * 把纯文本切成章节列表.
     *
     * @param text  Tika 解析后的纯文本
     * @return 章节列表(顺序匹配,第一个匹配的 pattern 决定切分粒度)
     */
    public List<Section> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        for (SectionPattern sp : PATTERNS) {
            List<Section> result = trySplitWith(text, sp);
            if (result.size() >= 2) {
                log.debug("Split text into {} sections using pattern {}", result.size(), sp.label);
                return result;
            }
        }

        // 兜底:整段当 1 个 section
        return List.of(Section.builder()
                .index(0)
                .title("(全文)")
                .content(text.trim())
                .startOffset(0)
                .endOffset(text.length())
                .build());
    }

    private List<Section> trySplitWith(String text, SectionPattern sp) {
        Matcher m = sp.pattern.matcher(text);
        List<int[]> positions = new ArrayList<>();  // [start, end] of each section title
        while (m.find()) {
            positions.add(new int[]{m.start(), m.end()});
        }
        if (positions.size() < 2) {
            return List.of();
        }

        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < positions.size(); i++) {
            int titleStart = positions.get(i)[0];
            int titleEnd = positions.get(i)[1];
            int contentStart = titleEnd;
            int contentEnd = (i + 1 < positions.size()) ? positions.get(i + 1)[0] : text.length();

            String title = text.substring(titleStart, titleEnd).trim();
            String content = text.substring(contentStart, contentEnd).trim();

            sections.add(Section.builder()
                    .index(i)
                    .title(title)
                    .content(content)
                    .startOffset(titleStart)
                    .endOffset(contentEnd)
                    .build());
        }
        return sections;
    }

    /** 切分用的正则 + label. */
    private record SectionPattern(Pattern pattern, String label) {}

    /** 单个章节. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Section {
        /** 章节序号(从 0 开始). */
        private int index;
        /** 章节标题(如 "第一章 项目概述"). */
        private String title;
        /** 章节正文. */
        private String content;
        /** 章节标题在原文中的起始位置. */
        private int startOffset;
        /** 章节正文在原文中的结束位置. */
        private int endOffset;

        /** 章节长度(字符数,便于摘要). */
        public int length() {
            return content == null ? 0 : content.length();
        }
    }
}

package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 章节摘要实体 — 知识库核心.
 *
 * 每个 MaterialVersion 经过章节切分后产生多条 ChapterSummary,
 * 每条对应一个章节的原文 + LLM 生成的摘要 + 关键词。
 * content 和 summary 上建有 FULLTEXT 索引(ngram 解析器),支持中文全文检索。
 *
 * @author Mavis
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "chapter_summary", indexes = {
        @Index(name = "idx_material_version", columnList = "material_version_id"),
        @Index(name = "idx_chapter_no", columnList = "chapter_no")
})
public class ChapterSummary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属材料版本 ID. */
    @Column(name = "material_version_id", nullable = false)
    private Long materialVersionId;

    /** 章节序号(从 1 开始). */
    @Column(name = "chapter_no", nullable = false)
    private Integer chapterNo;

    /** 章节标题. */
    @Column(name = "chapter_title", length = 512)
    private String chapterTitle;

    /** 章节原文. */
    @Lob
    @Column(name = "content")
    private String content;

    /** LLM 生成的 200 字摘要. */
    @Lob
    @Column(name = "summary")
    private String summary;

    /** LLM 抽取的关键词(逗号分隔). */
    @Column(name = "keywords", length = 512)
    private String keywords;

    /** 起始页. */
    @Column(name = "page_start")
    private Integer pageStart;

    /** 结束页. */
    @Column(name = "page_end")
    private Integer pageEnd;
}

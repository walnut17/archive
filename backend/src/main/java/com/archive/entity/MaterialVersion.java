package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 材料版本实体 — 一个材料(占位)的具体一次上传.
 *
 * 同一个 Material 下可以有多个 MaterialVersion(版本 1、版本 2、...)。
 * Material.currentVersionId 指向"当前生效版本"。
 *
 * 存储字段说明:
 * - storagePath:原始文件在本机的存储路径(相对 file-root)
 * - parsedTextPath:Tika 解析后的纯文本路径(相对 parsed-root)
 * - fileSize:文件大小(字节)
 * - mimeType:MIME 类型
 * - sha256:文件 SHA-256(用于去重)
 * - parsedAt:解析完成时间
 * - parseStatus:pending / success / failed
 *
 * @author Mavis
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "material_version", indexes = {
        @Index(name = "idx_mv_material_id", columnList = "material_id"),
        @Index(name = "idx_mv_sha256", columnList = "sha256"),
        @Index(name = "idx_mv_parse_status", columnList = "parse_status"),
        @Index(name = "idx_mv_uploaded_by", columnList = "uploaded_by")
})
public class MaterialVersion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属材料 ID. */
    @Column(name = "material_id", nullable = false)
    private Long materialId;

    /** 版本号(从 1 开始,自增). */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    /** 原始文件名(用户上传时的文件名). */
    @Column(name = "original_filename", nullable = false, length = 256)
    private String originalFilename;

    /** 存储路径(相对 file-root,如"project-001/proposal-001/material-001/v1/report.pdf"). */
    @Column(name = "storage_path", nullable = false, length = 1024)
    private String storagePath;

    /** Tika 解析后的纯文本路径(相对 parsed-root). */
    @Column(name = "parsed_text_path", length = 1024)
    private String parsedTextPath;

    /** 文件大小(字节). */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** MIME 类型. */
    @Column(name = "mime_type", length = 128)
    private String mimeType;

    /** SHA-256(去重/校验). */
    @Column(name = "sha256", length = 64)
    private String sha256;

    /** 解析状态:pending / running / success / failed. */
    @Column(name = "parse_status", nullable = false, length = 16)
    @Builder.Default
    private String parseStatus = "pending";

    /** 解析后的纯文本内容(M2 知识库问答用,FULLTEXT 索引字段). */
    @Lob
    @Column(name = "parsed_text")
    private String parsedText;

    /** 解析完成时间. */
    @Column(name = "parsed_at")
    private LocalDateTime parsedAt;

    /** 解析错误信息(失败时填). */
    @Column(name = "parse_error", length = 2000)
    private String parseError;

    /** 上传人(冗余,BaseEntity.createdBy 也有,这里方便查询). */
    @Column(name = "uploaded_by", length = 64)
    private String uploadedBy;

    /** 版本说明(上传时手填,"v2 修订财务数据"). */
    @Column(name = "change_note", length = 1000)
    private String changeNote;
}

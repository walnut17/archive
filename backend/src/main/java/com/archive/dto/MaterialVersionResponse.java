package com.archive.dto;

import com.archive.entity.MaterialVersion;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 材料版本响应 DTO.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialVersionResponse {

    private Long id;
    private Long materialId;
    private Integer versionNo;
    private String originalFilename;
    private String mimeType;
    private Long fileSize;
    private String sha256;
    private String parseStatus;
    private LocalDateTime parsedAt;
    private String parseError;
    private String uploadedBy;
    private String changeNote;
    private LocalDateTime createdAt;

    public static MaterialVersionResponse from(MaterialVersion v) {
        return MaterialVersionResponse.builder()
                .id(v.getId())
                .materialId(v.getMaterialId())
                .versionNo(v.getVersionNo())
                .originalFilename(v.getOriginalFilename())
                .mimeType(v.getMimeType())
                .fileSize(v.getFileSize())
                .sha256(v.getSha256())
                .parseStatus(v.getParseStatus())
                .parsedAt(v.getParsedAt())
                .parseError(v.getParseError())
                .uploadedBy(v.getUploadedBy())
                .changeNote(v.getChangeNote())
                .createdAt(v.getCreatedAt())
                .build();
    }
}

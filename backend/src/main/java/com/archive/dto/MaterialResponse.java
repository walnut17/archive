package com.archive.dto;

import com.archive.entity.Material;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 材料响应 DTO.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialResponse {

    private Long id;
    private Long proposalId;
    private String title;
    private String category;
    private Long currentVersionId;
    private String status;
    private String description;
    private String tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    /** 该材料下版本数(冗余,方便列表展示). */
    private long versionCount;

    public static MaterialResponse from(Material m, long versionCount) {
        return MaterialResponse.builder()
                .id(m.getId())
                .proposalId(m.getProposalId())
                .title(m.getTitle())
                .category(m.getCategory())
                .currentVersionId(m.getCurrentVersionId())
                .status(m.getStatus())
                .description(m.getDescription())
                .tags(m.getTags())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .createdBy(m.getCreatedBy())
                .updatedBy(m.getUpdatedBy())
                .versionCount(versionCount)
                .build();
    }
}

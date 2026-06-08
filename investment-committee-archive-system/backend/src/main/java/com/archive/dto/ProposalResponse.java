package com.archive.dto;

import com.archive.entity.Proposal;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 议案响应 DTO.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProposalResponse {

    private Long id;
    private String code;
    private String title;
    private Long projectId;
    private String type;
    private String summary;
    private String status;
    private LocalDate reviewedAt;
    private String decision;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    public static ProposalResponse from(Proposal p) {
        return ProposalResponse.builder()
                .id(p.getId())
                .code(p.getCode())
                .title(p.getTitle())
                .projectId(p.getProjectId())
                .type(p.getType())
                .summary(p.getSummary())
                .status(p.getStatus())
                .reviewedAt(p.getReviewedAt())
                .decision(p.getDecision())
                .remark(p.getRemark())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .createdBy(p.getCreatedBy())
                .updatedBy(p.getUpdatedBy())
                .build();
    }
}

package com.archive.dto;

import com.archive.entity.Project;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 项目响应 DTO — 列表/详情用.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectResponse {

    private Long id;
    private String code;
    private String name;
    private String category;
    private Long ownerId;
    private Long amountWan;
    private String summary;
    private String status;
    private LocalDate scheduledMeetingAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private String customerName;
    /** RI-69 脱敏标记. */
    private Boolean masked;
    private String displayName;
    private String displayAmount;
    private String unmaskRequestUrl;

    public static ProjectResponse from(Project p) {
        return ProjectResponse.builder()
                .id(p.getId())
                .code(p.getCode())
                .name(p.getName())
                .category(p.getCategory())
                .ownerId(p.getOwnerId())
                .amountWan(p.getAmountWan())
                .summary(p.getSummary())
                .status(p.getStatus())
                .scheduledMeetingAt(p.getScheduledMeetingAt())
                .remark(p.getRemark())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .createdBy(p.getCreatedBy())
                .updatedBy(p.getUpdatedBy())
                .customerName(p.getCustomerName())
                .masked(false)
                .build();
    }
}

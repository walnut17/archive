package com.archive.dto;

import com.archive.entity.Todo;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 待办响应 DTO — 列表/详情用.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoResponse {

    private Long id;
    private String title;
    private String source;
    private Long sourceRefId;
    private Long projectId;
    private Long ownerId;
    private String priority;
    private String status;
    private LocalDateTime dueAt;
    private LocalDateTime completedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    public static TodoResponse from(Todo t) {
        return TodoResponse.builder()
                .id(t.getId())
                .title(t.getTitle())
                .source(t.getSource())
                .sourceRefId(t.getSourceRefId())
                .projectId(t.getProjectId())
                .ownerId(t.getOwnerId())
                .priority(t.getPriority())
                .status(t.getStatus())
                .dueAt(t.getDueAt())
                .completedAt(t.getCompletedAt())
                .remark(t.getRemark())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .createdBy(t.getCreatedBy())
                .updatedBy(t.getUpdatedBy())
                .build();
    }
}

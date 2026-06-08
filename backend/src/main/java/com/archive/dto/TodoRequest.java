package com.archive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 待办请求 DTO — 创建/更新用.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoRequest {

    @NotBlank(message = "待办标题不能为空")
    @Size(max = 255)
    private String title;

    @NotBlank(message = "来源不能为空")
    @Size(max = 16)
    private String source;

    /** 来源 ID(timepoint.id / trigger_rule.id). */
    private Long sourceRefId;

    /** 关联项目 ID. */
    private Long projectId;

    /** 责任人 ID. */
    private Long ownerId;

    /** 优先级:low/medium/high/urgent. */
    @Size(max = 16)
    private String priority;

    /** 状态:pending/in_progress/done/cancelled/expired. */
    @Size(max = 16)
    private String status;

    /** 截止时间. */
    private LocalDateTime dueAt;

    /** 备注. */
    @Size(max = 1000)
    private String remark;
}

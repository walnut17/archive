package com.archive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;

/**
 * 项目请求 DTO — 创建/更新用.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectRequest {

    @NotBlank(message = "项目编号不能为空")
    @Size(max = 64)
    private String code;

    @NotBlank(message = "项目名称不能为空")
    @Size(max = 256)
    private String name;

    @Size(max = 64)
    private String category;

    private Long ownerId;

    private Long amountWan;

    @Size(max = 2000)
    private String summary;

    /** 状态:草稿 / 待审议 / 审议中 / 通过 / 暂缓 / 否决 / 撤回. */
    @Size(max = 32)
    private String status;

    private LocalDate scheduledMeetingAt;

    /** 可选: 创建前从该材料版本 AI 抽取预填 (失败时返回 failureType). */
    private Long materialVersionId;

    @Size(max = 2000)
    private String remark;
}

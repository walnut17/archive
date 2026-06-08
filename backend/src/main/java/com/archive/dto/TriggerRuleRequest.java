package com.archive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

/**
 * 触发规则请求 DTO — 创建/更新用.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TriggerRuleRequest {

    @NotBlank(message = "规则代码不能为空")
    @Size(max = 64)
    private String code;

    @NotBlank(message = "规则名称不能为空")
    @Size(max = 255)
    private String name;

    @Size(max = 1000)
    private String description;

    @NotBlank(message = "触发事件不能为空")
    @Size(max = 64)
    private String triggerEvent;

    @NotBlank(message = "触发条件不能为空")
    @Size(max = 1000)
    private String triggerCondition;

    /** 是否启用. */
    private Boolean enabled;

    /** 评估优先级 1-5. */
    private Integer priority;

    /** 关联动作列表. */
    private List<TriggerActionDto> actions;

    /**
     * 触发动作 DTO.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TriggerActionDto {

        /** 动作类型:create_todo/send_notification. */
        @NotBlank(message = "动作类型不能为空")
        @Size(max = 32)
        private String actionType;

        /** 动作模板 JSON. */
        @NotBlank(message = "动作模板不能为空")
        private String actionTemplate;

        /** 执行顺序. */
        private Integer sortOrder;
    }
}

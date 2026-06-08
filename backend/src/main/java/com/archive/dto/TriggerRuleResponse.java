package com.archive.dto;

import com.archive.entity.TriggerRule;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 触发规则响应 DTO.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TriggerRuleResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String triggerEvent;
    private String triggerCondition;
    private Boolean enabled;
    private Boolean builtin;
    private Integer priority;
    private LocalDateTime lastRunAt;
    private Integer lastMatchCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TriggerRuleResponse from(TriggerRule rule) {
        return TriggerRuleResponse.builder()
                .id(rule.getId())
                .code(rule.getCode())
                .name(rule.getName())
                .description(rule.getDescription())
                .triggerEvent(rule.getTriggerEvent())
                .triggerCondition(rule.getTriggerCondition())
                .enabled(rule.getEnabled())
                .builtin(rule.getBuiltin())
                .priority(rule.getPriority())
                .lastRunAt(rule.getLastRunAt())
                .lastMatchCount(rule.getLastMatchCount())
                .createdAt(rule.getCreatedAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }
}

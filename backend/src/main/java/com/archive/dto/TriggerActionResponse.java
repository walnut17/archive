package com.archive.dto;

import com.archive.entity.TriggerAction;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 触发动作响应 DTO.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TriggerActionResponse {

    private Long id;
    private Long ruleId;
    private String actionType;
    private String actionTemplate;
    private Integer sortOrder;
    private Boolean enabled;
    private LocalDateTime createdAt;

    public static TriggerActionResponse from(TriggerAction ta) {
        return TriggerActionResponse.builder()
                .id(ta.getId())
                .ruleId(ta.getRuleId())
                .actionType(ta.getActionType())
                .actionTemplate(ta.getActionTemplate())
                .sortOrder(ta.getSortOrder())
                .enabled(ta.getEnabled())
                .createdAt(ta.getCreatedAt())
                .build();
    }
}

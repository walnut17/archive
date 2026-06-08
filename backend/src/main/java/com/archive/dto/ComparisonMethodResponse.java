package com.archive.dto;

import com.archive.entity.ComparisonMethod;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 对比方法响应 DTO.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComparisonMethodResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String fromType;
    private String toType;
    private String promptTemplate;
    private String outputSchema;
    private Boolean builtin;
    private Boolean enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;

    public static ComparisonMethodResponse from(ComparisonMethod cm) {
        return ComparisonMethodResponse.builder()
                .id(cm.getId())
                .code(cm.getCode())
                .name(cm.getName())
                .description(cm.getDescription())
                .fromType(cm.getFromType())
                .toType(cm.getToType())
                .promptTemplate(cm.getPromptTemplate())
                .outputSchema(cm.getOutputSchema())
                .builtin(cm.getBuiltin())
                .enabled(cm.getEnabled())
                .sortOrder(cm.getSortOrder())
                .createdAt(cm.getCreatedAt())
                .build();
    }
}

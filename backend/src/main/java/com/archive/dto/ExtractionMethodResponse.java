package com.archive.dto;

import com.archive.entity.ExtractionMethod;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 抽取方法响应 DTO.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtractionMethodResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private String applyTo;
    private String promptTemplate;
    private String outputSchema;
    private Boolean builtin;
    private Boolean enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;

    public static ExtractionMethodResponse from(ExtractionMethod em) {
        return ExtractionMethodResponse.builder()
                .id(em.getId())
                .code(em.getCode())
                .name(em.getName())
                .description(em.getDescription())
                .applyTo(em.getApplyTo())
                .promptTemplate(em.getPromptTemplate())
                .outputSchema(em.getOutputSchema())
                .builtin(em.getBuiltin())
                .enabled(em.getEnabled())
                .sortOrder(em.getSortOrder())
                .createdAt(em.getCreatedAt())
                .build();
    }
}

package com.archive.dto;

import com.archive.entity.DictType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 字典分类响应 DTO.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DictTypeResponse {

    private Long id;
    private String typeCode;
    private String typeName;
    private String description;
    private Integer sortOrder;
    private Boolean isSystem;
    private Boolean enabled;
    private LocalDateTime createdAt;

    public static DictTypeResponse from(DictType dt) {
        return DictTypeResponse.builder()
                .id(dt.getId())
                .typeCode(dt.getTypeCode())
                .typeName(dt.getTypeName())
                .description(dt.getDescription())
                .sortOrder(dt.getSortOrder())
                .isSystem(dt.getIsSystem())
                .enabled(dt.getEnabled())
                .createdAt(dt.getCreatedAt())
                .build();
    }
}

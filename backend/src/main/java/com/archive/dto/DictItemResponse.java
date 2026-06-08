package com.archive.dto;

import com.archive.entity.DictItem;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 字典项响应 DTO.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DictItemResponse {

    private Long id;
    private String typeCode;
    private String itemKey;
    private String itemValue;
    private Integer sortOrder;
    private Boolean isDefault;
    private Boolean enabled;
    private Boolean isSystem;
    private String remark;
    private LocalDateTime createdAt;

    public static DictItemResponse from(DictItem di) {
        return DictItemResponse.builder()
                .id(di.getId())
                .typeCode(di.getTypeCode())
                .itemKey(di.getItemKey())
                .itemValue(di.getItemValue())
                .sortOrder(di.getSortOrder())
                .isDefault(di.getIsDefault())
                .enabled(di.getEnabled())
                .isSystem(di.getIsSystem())
                .remark(di.getRemark())
                .createdAt(di.getCreatedAt())
                .build();
    }
}

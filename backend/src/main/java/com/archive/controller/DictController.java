package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.dto.DictItemRequest;
import com.archive.dto.DictItemResponse;
import com.archive.dto.DictTypeRequest;
import com.archive.dto.DictTypeResponse;
import com.archive.entity.DictItem;
import com.archive.entity.DictType;
import com.archive.service.DictService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 字典 API.
 *
 * @author Mavis
 */
@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @GetMapping("/types")
    public ApiResponse<List<DictTypeResponse>> listTypes() {
        List<DictType> types = dictService.getDictTypes();
        List<DictTypeResponse> result = types.stream()
                .map(DictTypeResponse::from)
                .collect(Collectors.toList());
        return ApiResponse.ok(result);
    }

    @GetMapping("/items")
    public ApiResponse<List<DictItemResponse>> listItems(@RequestParam String typeCode) {
        List<DictItem> items = dictService.getDictItems(typeCode);
        List<DictItemResponse> result = items.stream()
                .map(DictItemResponse::from)
                .collect(Collectors.toList());
        return ApiResponse.ok(result);
    }

    @PostMapping("/types")
    public ApiResponse<DictTypeResponse> createType(@Valid @RequestBody DictTypeRequest req) {
        DictType dt = DictType.builder()
                .typeCode(req.getTypeCode())
                .typeName(req.getTypeName())
                .description(req.getDescription())
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
                .build();
        DictType created = dictService.createDictType(dt);
        return ApiResponse.ok(DictTypeResponse.from(created));
    }

    @PostMapping("/items")
    public ApiResponse<DictItemResponse> createItem(@Valid @RequestBody DictItemRequest req) {
        DictItem di = DictItem.builder()
                .typeCode(req.getTypeCode())
                .itemKey(req.getItemKey())
                .itemValue(req.getItemValue())
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
                .isDefault(req.getIsDefault() != null ? req.getIsDefault() : false)
                .enabled(req.getEnabled() != null ? req.getEnabled() : true)
                .remark(req.getRemark())
                .build();
        DictItem created = dictService.createDictItem(di);
        return ApiResponse.ok(DictItemResponse.from(created));
    }

    @DeleteMapping("/items/{id}")
    public ApiResponse<Void> deleteItem(@PathVariable Long id) {
        dictService.deleteDictItem(id);
        return ApiResponse.ok();
    }
}

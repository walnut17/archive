package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.dto.ComparisonMethodRequest;
import com.archive.dto.ComparisonMethodResponse;
import com.archive.dto.PageResponse;
import com.archive.entity.ComparisonMethod;
import com.archive.service.ComparisonMethodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 对比方法 API.
 *
 * @author Mavis
 */
@RestController
@RequestMapping("/api/comparison-methods")
@RequiredArgsConstructor
public class ComparisonMethodController {

    private final ComparisonMethodService comparisonMethodService;

    @GetMapping
    public ApiResponse<PageResponse<ComparisonMethodResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<ComparisonMethod> result = comparisonMethodService.listAll(page, size);
        return ApiResponse.ok(result.mapContent(ComparisonMethodResponse::from));
    }

    @GetMapping("/{id}")
    public ApiResponse<ComparisonMethodResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(ComparisonMethodResponse.from(comparisonMethodService.getById(id)));
    }

    @PostMapping
    public ApiResponse<ComparisonMethodResponse> create(@Valid @RequestBody ComparisonMethodRequest req) {
        ComparisonMethod cm = ComparisonMethod.builder()
                .code(req.getCode())
                .name(req.getName())
                .description(req.getDescription())
                .fromType(req.getFromType() != null ? req.getFromType() : "立项")
                .toType(req.getToType() != null ? req.getToType() : "申请")
                .promptTemplate(req.getPromptTemplate())
                .outputSchema(req.getOutputSchema())
                .enabled(req.getEnabled() != null ? req.getEnabled() : true)
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
                .build();
        ComparisonMethod created = comparisonMethodService.create(cm);
        return ApiResponse.ok(ComparisonMethodResponse.from(created));
    }

    @PutMapping("/{id}")
    public ApiResponse<ComparisonMethodResponse> update(@PathVariable Long id, @Valid @RequestBody ComparisonMethodRequest req) {
        ComparisonMethod cm = ComparisonMethod.builder()
                .code(req.getCode())
                .name(req.getName())
                .description(req.getDescription())
                .fromType(req.getFromType())
                .toType(req.getToType())
                .promptTemplate(req.getPromptTemplate())
                .outputSchema(req.getOutputSchema())
                .enabled(req.getEnabled())
                .sortOrder(req.getSortOrder())
                .build();
        ComparisonMethod updated = comparisonMethodService.update(id, cm);
        return ApiResponse.ok(ComparisonMethodResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        comparisonMethodService.delete(id);
        return ApiResponse.ok();
    }
}

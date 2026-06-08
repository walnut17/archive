package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.dto.ExtractionMethodRequest;
import com.archive.dto.ExtractionMethodResponse;
import com.archive.dto.PageResponse;
import com.archive.entity.ExtractionMethod;
import com.archive.service.ExtractionMethodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 抽取方法 API.
 *
 * @author Mavis
 */
@RestController
@RequestMapping("/api/extraction-methods")
@RequiredArgsConstructor
public class ExtractionMethodController {

    private final ExtractionMethodService extractionMethodService;

    @GetMapping
    public ApiResponse<PageResponse<ExtractionMethodResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<ExtractionMethod> result = extractionMethodService.listAll(page, size);
        return ApiResponse.ok(result.mapContent(ExtractionMethodResponse::from));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExtractionMethodResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(ExtractionMethodResponse.from(extractionMethodService.getById(id)));
    }

    @PostMapping
    public ApiResponse<ExtractionMethodResponse> create(@Valid @RequestBody ExtractionMethodRequest req) {
        ExtractionMethod em = ExtractionMethod.builder()
                .code(req.getCode())
                .name(req.getName())
                .description(req.getDescription())
                .applyTo(req.getApplyTo() != null ? req.getApplyTo() : "material")
                .promptTemplate(req.getPromptTemplate())
                .outputSchema(req.getOutputSchema())
                .enabled(req.getEnabled() != null ? req.getEnabled() : true)
                .sortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0)
                .build();
        ExtractionMethod created = extractionMethodService.create(em);
        return ApiResponse.ok(ExtractionMethodResponse.from(created));
    }

    @PutMapping("/{id}")
    public ApiResponse<ExtractionMethodResponse> update(@PathVariable Long id, @Valid @RequestBody ExtractionMethodRequest req) {
        ExtractionMethod em = ExtractionMethod.builder()
                .code(req.getCode())
                .name(req.getName())
                .description(req.getDescription())
                .applyTo(req.getApplyTo())
                .promptTemplate(req.getPromptTemplate())
                .outputSchema(req.getOutputSchema())
                .enabled(req.getEnabled())
                .sortOrder(req.getSortOrder())
                .build();
        ExtractionMethod updated = extractionMethodService.update(id, em);
        return ApiResponse.ok(ExtractionMethodResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        extractionMethodService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/by-apply")
    public ApiResponse<List<ExtractionMethodResponse>> getByApplyTo(@RequestParam String applyTo) {
        List<ExtractionMethod> methods = extractionMethodService.getEnabledByApplyTo(applyTo);
        List<ExtractionMethodResponse> result = methods.stream()
                .map(ExtractionMethodResponse::from)
                .collect(Collectors.toList());
        return ApiResponse.ok(result);
    }
}

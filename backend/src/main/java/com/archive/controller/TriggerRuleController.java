package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.dto.PageResponse;
import com.archive.dto.TriggerActionResponse;
import com.archive.dto.TriggerRuleRequest;
import com.archive.dto.TriggerRuleResponse;
import com.archive.entity.TriggerAction;
import com.archive.entity.TriggerRule;
import com.archive.service.TriggerRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 触发规则 API.
 *
 * @author Mavis
 */
@RestController
@RequestMapping("/api/trigger-rules")
@RequiredArgsConstructor
public class TriggerRuleController {

    private final TriggerRuleService triggerRuleService;

    @GetMapping
    public ApiResponse<PageResponse<TriggerRuleResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<TriggerRule> result = triggerRuleService.listAll(page, size);
        return ApiResponse.ok(result.mapContent(TriggerRuleResponse::from));
    }

    @GetMapping("/{id}")
    public ApiResponse<TriggerRuleResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(TriggerRuleResponse.from(triggerRuleService.getById(id)));
    }

    @PostMapping
    public ApiResponse<TriggerRuleResponse> create(@Valid @RequestBody TriggerRuleRequest req) {
        TriggerRule rule = TriggerRule.builder()
                .code(req.getCode())
                .name(req.getName())
                .description(req.getDescription())
                .triggerEvent(req.getTriggerEvent())
                .triggerCondition(req.getTriggerCondition())
                .enabled(req.getEnabled() != null ? req.getEnabled() : true)
                .priority(req.getPriority() != null ? req.getPriority() : 3)
                .build();

        List<TriggerAction> actions = null;
        if (req.getActions() != null) {
            actions = req.getActions().stream()
                    .map(a -> TriggerAction.builder()
                            .actionType(a.getActionType())
                            .actionTemplate(a.getActionTemplate())
                            .sortOrder(a.getSortOrder() != null ? a.getSortOrder() : 1)
                            .build())
                    .collect(Collectors.toList());
        }

        TriggerRule created = triggerRuleService.create(rule, actions);
        return ApiResponse.ok(TriggerRuleResponse.from(created));
    }

    @PutMapping("/{id}")
    public ApiResponse<TriggerRuleResponse> update(@PathVariable Long id, @Valid @RequestBody TriggerRuleRequest req) {
        TriggerRule rule = TriggerRule.builder()
                .code(req.getCode())
                .name(req.getName())
                .description(req.getDescription())
                .triggerEvent(req.getTriggerEvent())
                .triggerCondition(req.getTriggerCondition())
                .enabled(req.getEnabled())
                .priority(req.getPriority())
                .build();

        List<TriggerAction> actions = null;
        if (req.getActions() != null) {
            actions = req.getActions().stream()
                    .map(a -> TriggerAction.builder()
                            .actionType(a.getActionType())
                            .actionTemplate(a.getActionTemplate())
                            .sortOrder(a.getSortOrder() != null ? a.getSortOrder() : 1)
                            .build())
                    .collect(Collectors.toList());
        }

        TriggerRule updated = triggerRuleService.update(id, rule, actions);
        return ApiResponse.ok(TriggerRuleResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        triggerRuleService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping("/by-event")
    public ApiResponse<List<TriggerRuleResponse>> getByEvent(@RequestParam String event) {
        List<TriggerRule> rules = triggerRuleService.getEnabledByEvent(event);
        List<TriggerRuleResponse> result = rules.stream()
                .map(TriggerRuleResponse::from)
                .collect(Collectors.toList());
        return ApiResponse.ok(result);
    }

    @GetMapping("/{id}/actions")
    public ApiResponse<List<TriggerActionResponse>> getActions(@PathVariable Long id) {
        List<TriggerAction> actions = triggerRuleService.getActionsByRule(id);
        List<TriggerActionResponse> result = actions.stream()
                .map(TriggerActionResponse::from)
                .collect(Collectors.toList());
        return ApiResponse.ok(result);
    }
}

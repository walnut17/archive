package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.dto.PageResponse;
import com.archive.dto.ProposalRequest;
import com.archive.dto.ProposalResponse;
import com.archive.entity.Proposal;
import com.archive.service.ProposalService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.archive.security.JwtAuthFilter;

import java.util.Map;

/**
 * 议案 API.
 *
 * @author Mavis
 */
@RestController
@RequestMapping("/api/proposals")
@RequiredArgsConstructor
public class ProposalController {

    private final ProposalService proposalService;

    @GetMapping
    public ApiResponse<PageResponse<ProposalResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        PageResponse<Proposal> result = proposalService.list(page, size, projectId, status, keyword);
        return ApiResponse.ok(result.mapContent(ProposalResponse::from));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProposalResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(ProposalResponse.from(proposalService.getById(id)));
    }

    @PostMapping
    public ApiResponse<ProposalResponse> create(@Valid @RequestBody ProposalRequest req) {
        Proposal created = proposalService.create(req);
        return ApiResponse.ok(ProposalResponse.from(created));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProposalResponse> update(@PathVariable Long id, @Valid @RequestBody ProposalRequest req) {
        Proposal updated = proposalService.update(id, req);
        return ApiResponse.ok(ProposalResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','PM')")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtAuthFilter.AuthenticatedUser user) {
        Long userId = user != null ? user.id() : null;
        proposalService.softDelete(id, userId);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/regenerate-summary")
    public ApiResponse<ProposalResponse> regenerateSummary(@PathVariable Long id) {
        Proposal updated = proposalService.regenerateSummary(id);
        return ApiResponse.ok(ProposalResponse.from(updated));
    }

    @PatchMapping("/{id}/decision")
    @PreAuthorize("hasAnyRole('ADMIN','COMMITTEE')")
    public ApiResponse<ProposalResponse> updateDecision(
            @PathVariable Long id,
            @RequestBody DecisionRequest dto) {
        Proposal updated = proposalService.updateDecision(id, dto.getMeetingResult(), dto.getConditionText());
        return ApiResponse.ok(ProposalResponse.from(updated));
    }

    @PostMapping("/reserve")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','PM')")
    public ApiResponse<Map<String, Object>> reserve(@RequestBody ReserveRequest dto) {
        Proposal p = proposalService.reserve(dto.getSeriesCode(), dto.getProjectId());
        return ApiResponse.ok(Map.of(
                "proposalCode", p.getCode(),
                "proposalId", p.getId(),
                "expiresAt", p.getReservedAt().plusHours(24)
        ));
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY','PM')")
    public ApiResponse<Void> revoke(@PathVariable Long id) {
        proposalService.revoke(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/change-series")
    @PreAuthorize("hasAnyRole('ADMIN','SECRETARY')")
    public ApiResponse<ProposalResponse> changeSeries(
            @PathVariable Long id,
            @RequestBody ChangeSeriesRequest dto) {
        Proposal p = proposalService.changeSeries(id, dto.getSeriesCode());
        return ApiResponse.ok(ProposalResponse.from(p));
    }

    @Data
    public static class DecisionRequest {
        private String meetingResult;
        private String conditionText;
    }

    @Data
    public static class ReserveRequest {
        private String seriesCode;
        private Long projectId;
    }

    @Data
    public static class ChangeSeriesRequest {
        private String seriesCode;
    }
}

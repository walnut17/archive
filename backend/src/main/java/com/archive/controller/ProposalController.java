package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.dto.PageResponse;
import com.archive.dto.ProposalRequest;
import com.archive.dto.ProposalResponse;
import com.archive.entity.Proposal;
import com.archive.service.ProposalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    public ApiResponse<Void> delete(@PathVariable Long id) {
        proposalService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/regenerate-summary")
    public ApiResponse<ProposalResponse> regenerateSummary(@PathVariable Long id) {
        Proposal updated = proposalService.regenerateSummary(id);
        return ApiResponse.ok(ProposalResponse.from(updated));
    }
}

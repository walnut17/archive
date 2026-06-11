package com.archive.controller;

import com.archive.dto.BoardResponse;
import com.archive.service.ProjectBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 项目看板 API (RI-62).
 */
@RestController
@RequestMapping("/api/projects/board")
@RequiredArgsConstructor
public class ProjectBoardController {

    private final ProjectBoardService projectBoardService;

    @GetMapping
    public ResponseEntity<BoardResponse> list(
            @RequestParam(defaultValue = "table") String view,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String stage,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        BoardResponse resp = projectBoardService.list(view, region, stage, sort, order, page, size);
        return ResponseEntity.ok(resp);
    }
}

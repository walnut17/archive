package com.archive.controller;

import com.archive.entity.ImportBatch;
import com.archive.entity.ImportError;
import com.archive.service.ImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 旧系统 Excel 导入 API (RI-68).
 */
@RestController
@RequestMapping("/api/admin/import")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ImportController {

    private final ImportService importService;

    @PostMapping(value = "/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportBatch> importExcel(
            @PathVariable String type,
            @RequestParam("file") MultipartFile file) {
        ImportBatch batch = importService.importExcel(type, file);
        return ResponseEntity.ok(batch);
    }

    @GetMapping("/{batchId}/errors")
    public ResponseEntity<List<ImportError>> getErrors(@PathVariable Long batchId) {
        return ResponseEntity.ok(importService.getErrors(batchId));
    }
}

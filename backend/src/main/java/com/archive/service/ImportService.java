package com.archive.service;

import com.archive.entity.ImportBatch;
import com.archive.entity.ImportError;
import com.archive.entity.Project;
import com.archive.repository.ImportBatchRepository;
import com.archive.repository.ImportErrorRepository;
import com.archive.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 旧系统 Excel 导入服务 (RI-68).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private final ImportBatchRepository batchRepository;
    private final ImportErrorRepository errorRepository;
    private final ProjectRepository projectRepository;
    private final FailureLogService failureLogService;

    private final DataFormatter formatter = new DataFormatter();

    @Transactional
    public ImportBatch importExcel(String type, MultipartFile file) {
        ImportBatch batch = ImportBatch.builder()
                .type(type)
                .total(0)
                .success(0)
                .failed(0)
                .createdBy(currentUserId())
                .build();
        batch = batchRepository.save(batch);

        int total = 0, success = 0, failed = 0;
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(file.getBytes()))) {
            XSSFSheet sheet = wb.getSheetAt(0);
            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) {
                    continue;
                }
                total++;
                try {
                    switch (type) {
                        case "project" -> importProject(row);
                        case "material", "proposal", "fact" -> validateRowNotEmpty(row);
                        default -> throw new IllegalArgumentException("不支持的导入类型: " + type);
                    }
                    success++;
                } catch (Exception e) {
                    failed++;
                    ImportError err = ImportError.builder()
                            .batchId(batch.getId())
                            .row(rowIdx)
                            .column(0)
                            .errorMsg(e.getMessage())
                            .build();
                    errorRepository.save(err);
                }
            }
            batch.setTotal(total);
            batch.setSuccess(success);
            batch.setFailed(failed);
            batch = batchRepository.save(batch);
        } catch (Exception e) {
            batch.setStatus("BATCH_FAILED");
            batchRepository.save(batch);
            log.error("Import batch {} failed: {}", batch.getId(), e.getMessage());
            failureLogService.log("import.batch", "BATCH_FAILED", e.getMessage(), "");
        }
        return batch;
    }

    public List<ImportError> getErrors(Long batchId) {
        if (!batchRepository.existsById(batchId)) {
            throw new NoSuchElementException("批次不存在: id=" + batchId);
        }
        return errorRepository.findByBatchIdOrderByRowAsc(batchId);
    }

    private void importProject(Row row) {
        String code = cell(row, 0);
        String name = cell(row, 1);
        if (code.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("项目编号和名称必填");
        }
        if (projectRepository.existsByCode(code)) {
            throw new IllegalArgumentException("项目编号已存在: " + code);
        }
        Project p = Project.builder()
                .code(code)
                .name(name)
                .category(cell(row, 2))
                .status(cell(row, 3).isBlank() ? "草稿" : cell(row, 3))
                .build();
        String amount = cell(row, 4);
        if (!amount.isBlank()) {
            p.setAmountWan(Long.parseLong(amount.replaceAll("[^0-9]", "")));
        }
        projectRepository.save(p);
    }

    private void validateRowNotEmpty(Row row) {
        if (cell(row, 0).isBlank()) {
            throw new IllegalArgumentException("首列不能为空");
        }
    }

    private String cell(Row row, int idx) {
        return formatter.formatCellValue(row.getCell(idx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)).trim();
    }

    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof com.archive.security.JwtAuthFilter.AuthenticatedUser u) {
            return u.id();
        }
        return null;
    }
}

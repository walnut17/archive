package com.archive.engine;

import com.archive.entity.Material;
import com.archive.entity.MaterialVersion;
import com.archive.entity.Proposal;
import com.archive.entity.Timepoint;
import com.archive.repository.MaterialRepository;
import com.archive.repository.MaterialVersionRepository;
import com.archive.repository.ProposalRepository;
import com.archive.service.TimepointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 时点抽取引擎 — 从文档中提取关键时间节点.
 *
 * <p>复用 ExtractionEngine 的能力,使用 DEFAULT_TIMEPOINT 方法从材料解析文本中
 * 抽取时点数据(如到期日、审议日、付款日等),过滤置信度 >= 0.6 的条目,
 * 自动创建 Timepoint 实体并关联到项目。
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimepointExtractor {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ExtractionEngine extractionEngine;
    private final TimepointService timepointService;
    private final MaterialVersionRepository materialVersionRepository;
    private final MaterialRepository materialRepository;
    private final ProposalRepository proposalRepository;

    /**
     * 抽取时点并创建 Timepoint 实体.
     *
     * @param materialVersionId 材料版本 ID
     * @return 成功创建的时点 ID 列表(高置信度条目)
     */
    @Async("taskExecutor")
    public List<Long> extractTimepoints(Long materialVersionId) {
        try {
            // 1. 调用抽取引擎获取时点数据
            Map<String, Object> extracted = extractionEngine.extract(materialVersionId, "DEFAULT_TIMEPOINT");
            if (extracted == null || extracted.isEmpty()) {
                log.info("No timepoints extracted from materialVersionId={}", materialVersionId);
                return Collections.emptyList();
            }

            // 2. 获取项目 ID
            Long projectId = resolveProjectId(materialVersionId);
            if (projectId == null) {
                log.warn("Cannot resolve projectId for materialVersionId={}, skip timepoint creation",
                        materialVersionId);
                return Collections.emptyList();
            }

            // 3. 解析时点列表
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> timepointEntries = (List<Map<String, Object>>) extracted.get("timepoints");
            if (timepointEntries == null || timepointEntries.isEmpty()) {
                return Collections.emptyList();
            }

            List<Long> createdIds = new ArrayList<>();

            for (Map<String, Object> entry : timepointEntries) {
                try {
                    // 4. 解析字段
                    double confidence = parseDouble(entry.get("confidence"));
                    if (confidence < 0.6) {
                        log.debug("Skip timepoint with low confidence: {}", confidence);
                        continue;
                    }

                    String name = String.valueOf(entry.getOrDefault("name", ""));
                    if (name.isBlank()) {
                        log.warn("Skip timepoint with empty name");
                        continue;
                    }

                    String dateStr = String.valueOf(entry.getOrDefault("date", ""));
                    LocalDate dueAt = LocalDate.parse(dateStr, DATE_FORMATTER);

                    // 5. 创建 Timepoint
                    Timepoint timepoint = timepointService.create(
                            projectId,
                            name,
                            dueAt,
                            null,   // type, LLM 猜测不直接使用
                            null,   // ownerId, 后续人工指派
                            materialVersionId,
                            "30,7,1,0",
                            String.valueOf(entry.getOrDefault("source_text", "")),
                            null,   // sourcePage
                            null    // remark
                    );
                    createdIds.add(timepoint.getId());
                    log.info("Created timepoint id={}, name={}, dueAt={}", timepoint.getId(), name, dueAt);

                } catch (Exception e) {
                    log.warn("Failed to create timepoint from entry: {}", entry, e);
                }
            }

            log.info("Timepoint extraction completed for materialVersionId={}, created={}",
                    materialVersionId, createdIds.size());
            return createdIds;

        } catch (Exception e) {
            log.warn("Timepoint extraction failed for materialVersionId={}: {}",
                    materialVersionId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 从材料版本逆向解析项目 ID: materialVersion -> material -> proposal -> project.
     */
    private Long resolveProjectId(Long materialVersionId) {
        MaterialVersion mv = materialVersionRepository.findById(materialVersionId).orElse(null);
        if (mv == null) return null;
        Material material = materialRepository.findById(mv.getMaterialId()).orElse(null);
        if (material == null) return null;
        Proposal proposal = proposalRepository.findById(material.getProposalId()).orElse(null);
        if (proposal == null) return null;
        return proposal.getProjectId();
    }

    private double parseDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}

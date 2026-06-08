package com.archive.service;

import com.archive.dto.PageResponse;
import com.archive.entity.Timepoint;
import com.archive.repository.TimepointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 时间节点业务逻辑.
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimepointService {

    private final TimepointRepository timepointRepository;

    /** 合法状态集合. */
    private static final java.util.Set<String> VALID_STATUSES = java.util.Set.of(
            "待提醒", "已提醒", "已处理", "已逾期", "已作废"
    );

    /** 合法时点类型. */
    private static final java.util.Set<String> VALID_TYPES = java.util.Set.of(
            "到期", "审议", "披露", "付款", "法律意见", "工商变更", "其他"
    );

    /**
     * 创建时点.
     */
    @Transactional
    public Timepoint create(Long projectId, String name, LocalDate dueAt, String type,
                            Long ownerId, Long materialVersionId, String reminderDays,
                            String sourceText, Integer sourcePage, String remark) {
        if (projectId == null) {
            throw new IllegalArgumentException("项目 ID 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("时点事项描述不能为空");
        }
        if (dueAt == null) {
            throw new IllegalArgumentException("截止日期不能为空");
        }
        if (type != null && !VALID_TYPES.contains(type)) {
            throw new IllegalArgumentException("非法时点类型: " + type);
        }

        Timepoint t = Timepoint.builder()
                .projectId(projectId)
                .name(name.trim())
                .dueAt(dueAt)
                .type(type != null ? type : "其他")
                .ownerId(ownerId)
                .materialVersionId(materialVersionId)
                .reminderDays(reminderDays != null ? reminderDays : "30,7,1,0")
                .sourceText(sourceText)
                .sourcePage(sourcePage)
                .remark(remark)
                .status("待提醒")
                .extractedBy("manual")
                .build();
        return timepointRepository.save(t);
    }

    /**
     * 更新时点.
     */
    @Transactional
    public Timepoint update(Long id, String name, LocalDate dueAt, String type,
                            Long ownerId, String status, String reminderDays,
                            String sourceText, Integer sourcePage, String remark) {
        Timepoint t = timepointRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("时点不存在: id=" + id));

        if (name != null && !name.isBlank()) {
            t.setName(name.trim());
        }
        if (dueAt != null) {
            t.setDueAt(dueAt);
        }
        if (type != null) {
            if (!VALID_TYPES.contains(type)) {
                throw new IllegalArgumentException("非法时点类型: " + type);
            }
            t.setType(type);
        }
        if (ownerId != null) {
            t.setOwnerId(ownerId);
        }
        if (status != null) {
            if (!VALID_STATUSES.contains(status)) {
                throw new IllegalArgumentException("非法状态: " + status);
            }
            t.setStatus(status);
        }
        if (reminderDays != null) {
            t.setReminderDays(reminderDays);
        }
        if (sourceText != null) {
            t.setSourceText(sourceText);
        }
        if (sourcePage != null) {
            t.setSourcePage(sourcePage);
        }
        if (remark != null) {
            t.setRemark(remark);
        }
        return timepointRepository.save(t);
    }

    /**
     * 删除时点.
     */
    @Transactional
    public void delete(Long id) {
        Timepoint t = timepointRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("时点不存在: id=" + id));
        timepointRepository.delete(t);
        log.info("已删除时点 id={}", id);
    }

    /**
     * 按 ID 查询时点.
     */
    public Timepoint getById(Long id) {
        return timepointRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("时点不存在: id=" + id));
    }

    /**
     * 按项目分页查询时点.
     */
    public PageResponse<Timepoint> listByProject(Long projectId, int page, int size) {
        Page<Timepoint> result = timepointRepository.findByProjectId(projectId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dueAt")));
        return PageResponse.of(result);
    }

    /**
     * 查询日期范围内的时点.
     */
    public List<Timepoint> getUpcoming(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("起始日期和截止日期不能为空");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("起始日期不能晚于截止日期");
        }
        return timepointRepository.findByDueAtBetween(from, to);
    }

    /**
     * 标记逾期:将状态为"待提醒"且截止日期早于今天的时点标记为"已逾期".
     */
    @Transactional
    public int markOverdue() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<Timepoint> overdueList = timepointRepository.findByDueAtBetween(
                LocalDate.of(1970, 1, 1), yesterday);
        int count = 0;
        for (Timepoint t : overdueList) {
            if ("待提醒".equals(t.getStatus())) {
                t.setStatus("已逾期");
                timepointRepository.save(t);
                count++;
            }
        }
        if (count > 0) {
            log.info("已标记 {} 个时点为已逾期", count);
        }
        return count;
    }
}

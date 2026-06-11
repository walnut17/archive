package com.archive.service;

import com.archive.dto.FactEventDiff;
import com.archive.entity.ProjectFactEvent;
import com.archive.repository.ProjectFactEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 关键事实事件服务 (RI-66 diff).
 */
@Service
@RequiredArgsConstructor
public class ProjectFactEventService {

    private final ProjectFactEventRepository repository;

    public List<ProjectFactEvent> listByProject(Long projectId) {
        return repository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public FactEventDiff getDiff(Long eventId) {
        ProjectFactEvent evt = repository.findById(eventId)
                .orElseThrow(() -> new NoSuchElementException("事实事件不存在: id=" + eventId));

        ProjectFactEvent before = repository
                .findTopByProjectIdAndFactTypeAndCreatedAtBeforeAndDeletedAtIsNullOrderByCreatedAtDesc(
                        evt.getProjectId(), evt.getFactType(), evt.getCreatedAt())
                .orElse(null);

        return new FactEventDiff(
                before != null ? before.getFactValue() : null,
                evt.getFactValue(),
                evt.getEvidence()
        );
    }
}

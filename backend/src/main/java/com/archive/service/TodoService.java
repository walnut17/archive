package com.archive.service;

import com.archive.dto.PageResponse;
import com.archive.entity.Todo;
import com.archive.repository.TodoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

/**
 * 待办事项业务逻辑.
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoService {

    private final TodoRepository todoRepository;

    /** 合法优先级. */
    private static final java.util.Set<String> VALID_PRIORITIES = java.util.Set.of(
            "low", "medium", "high", "urgent"
    );

    /** 合法状态. */
    private static final java.util.Set<String> VALID_STATUSES = java.util.Set.of(
            "pending", "in_progress", "done", "cancelled", "expired"
    );

    /** 合法来源. */
    private static final java.util.Set<String> VALID_SOURCES = java.util.Set.of(
            "auto_timepoint", "manual", "trigger"
    );

    /**
     * 创建待办.
     */
    @Transactional
    public Todo create(String title, String source, Long sourceRefId, Long projectId,
                       Long ownerId, String priority, LocalDateTime dueAt) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("待办标题不能为空");
        }
        if (source == null || !VALID_SOURCES.contains(source)) {
            throw new IllegalArgumentException("非法来源: " + source);
        }
        if (priority != null && !VALID_PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("非法优先级: " + priority);
        }

        Todo t = Todo.builder()
                .title(title.trim())
                .source(source)
                .sourceRefId(sourceRefId)
                .projectId(projectId)
                .ownerId(ownerId)
                .priority(priority != null ? priority : "medium")
                .status("pending")
                .dueAt(dueAt)
                .build();
        return todoRepository.save(t);
    }

    /**
     * 更新待办.
     */
    @Transactional
    public Todo update(Long id, String title, String source, Long sourceRefId,
                       Long projectId, Long ownerId, String priority, String status, LocalDateTime dueAt) {
        Todo t = todoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("待办不存在: id=" + id));

        if (title != null && !title.isBlank()) {
            t.setTitle(title.trim());
        }
        if (source != null) {
            if (!VALID_SOURCES.contains(source)) {
                throw new IllegalArgumentException("非法来源: " + source);
            }
            t.setSource(source);
        }
        if (sourceRefId != null) {
            t.setSourceRefId(sourceRefId);
        }
        if (projectId != null) {
            t.setProjectId(projectId);
        }
        if (ownerId != null) {
            t.setOwnerId(ownerId);
        }
        if (priority != null) {
            if (!VALID_PRIORITIES.contains(priority)) {
                throw new IllegalArgumentException("非法优先级: " + priority);
            }
            t.setPriority(priority);
        }
        if (status != null) {
            if (!VALID_STATUSES.contains(status)) {
                throw new IllegalArgumentException("非法状态: " + status);
            }
            t.setStatus(status);
            if ("done".equals(status) && t.getCompletedAt() == null) {
                t.setCompletedAt(LocalDateTime.now());
            }
        }
        if (dueAt != null) {
            t.setDueAt(dueAt);
        }
        return todoRepository.save(t);
    }

    /**
     * 删除待办.
     */
    @Transactional
    public void delete(Long id) {
        Todo t = todoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("待办不存在: id=" + id));
        todoRepository.delete(t);
        log.info("已删除待办 id={}", id);
    }

    /**
     * 按 ID 查询待办.
     */
    public Todo getById(Long id) {
        return todoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("待办不存在: id=" + id));
    }

    /**
     * 按责任人分页查询.
     */
    public PageResponse<Todo> listByOwner(Long ownerId, int page, int size) {
        Page<Todo> result = todoRepository.findByOwnerId(ownerId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dueAt")));
        return PageResponse.of(result);
    }

    /**
     * 按状态分页查询.
     */
    public PageResponse<Todo> listByStatus(String status, int page, int size) {
        if (status == null || !VALID_STATUSES.contains(status)) {
            throw new IllegalArgumentException("非法状态: " + status);
        }
        Page<Todo> result = todoRepository.findByStatus(status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(result);
    }

    /**
     * 按项目分页查询.
     */
    public PageResponse<Todo> listByProject(Long projectId, int page, int size) {
        Page<Todo> result = todoRepository.findByProjectId(projectId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(result);
    }

    /**
     * 完成待办:设置状态为 done 并记录完成时间.
     */
    @Transactional
    public Todo complete(Long id) {
        Todo t = todoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("待办不存在: id=" + id));
        t.setStatus("done");
        t.setCompletedAt(LocalDateTime.now());
        return todoRepository.save(t);
    }

    /**
     * 从触发规则创建待办(与 create 逻辑相同,语义区分).
     */
    @Transactional
    public Todo createFromTrigger(String title, String source, Long sourceRefId, Long projectId,
                                  Long ownerId, String priority, LocalDateTime dueAt) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("待办标题不能为空");
        }
        if (source == null || !VALID_SOURCES.contains(source)) {
            throw new IllegalArgumentException("非法来源: " + source);
        }
        if (priority != null && !VALID_PRIORITIES.contains(priority)) {
            throw new IllegalArgumentException("非法优先级: " + priority);
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("责任人不能为空");
        }

        Todo t = Todo.builder()
                .title(title.trim())
                .source(source)
                .sourceRefId(sourceRefId)
                .projectId(projectId)
                .ownerId(ownerId)
                .priority(priority != null ? priority : "medium")
                .status("pending")
                .dueAt(dueAt)
                .build();
        return todoRepository.save(t);
    }
}

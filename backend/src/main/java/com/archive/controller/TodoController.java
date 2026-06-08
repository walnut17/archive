package com.archive.controller;

import com.archive.common.ApiResponse;
import com.archive.dto.PageResponse;
import com.archive.dto.TodoRequest;
import com.archive.dto.TodoResponse;
import com.archive.entity.Todo;
import com.archive.service.TodoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 待办 API.
 *
 * @author Mavis
 */
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    @GetMapping
    public ApiResponse<PageResponse<TodoResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long projectId) {
        PageResponse<Todo> result;
        if (ownerId != null) {
            result = todoService.listByOwner(ownerId, page, size);
        } else if (status != null) {
            result = todoService.listByStatus(status, page, size);
        } else if (projectId != null) {
            result = todoService.listByProject(projectId, page, size);
        } else {
            // 默认按创建时间倒序全量查询
            result = todoService.listByOwner(null, page, size);
        }
        return ApiResponse.ok(result.mapContent(TodoResponse::from));
    }

    @GetMapping("/{id}")
    public ApiResponse<TodoResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(TodoResponse.from(todoService.getById(id)));
    }

    @PostMapping
    public ApiResponse<TodoResponse> create(@Valid @RequestBody TodoRequest req) {
        Todo created = todoService.create(
                req.getTitle(),
                req.getSource(),
                req.getSourceRefId(),
                req.getProjectId(),
                req.getOwnerId(),
                req.getPriority(),
                req.getDueAt());
        return ApiResponse.ok(TodoResponse.from(created));
    }

    @PutMapping("/{id}")
    public ApiResponse<TodoResponse> update(@PathVariable Long id, @Valid @RequestBody TodoRequest req) {
        Todo updated = todoService.update(
                id,
                req.getTitle(),
                req.getSource(),
                req.getSourceRefId(),
                req.getProjectId(),
                req.getOwnerId(),
                req.getPriority(),
                req.getStatus(),
                req.getDueAt());
        return ApiResponse.ok(TodoResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        todoService.delete(id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/complete")
    public ApiResponse<TodoResponse> complete(@PathVariable Long id) {
        Todo completed = todoService.complete(id);
        return ApiResponse.ok(TodoResponse.from(completed));
    }
}

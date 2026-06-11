package com.archive.service;

import com.archive.dto.BoardResponse;
import com.archive.dto.ProjectBoardItem;
import com.archive.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 项目看板服务 (RI-62).
 */
@Service
@RequiredArgsConstructor
public class ProjectBoardService {

    private final ProjectRepository projectRepository;

    public BoardResponse list(String view, String region, String stage,
                              String sort, String order, int page, int size) {
        Sort.Direction dir = "asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = mapSortField(sort);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), size, Sort.by(dir, sortField));

        Page<ProjectBoardItem> result = projectRepository.findBoardView(region, stage, pageable);
        List<ProjectBoardItem> items = result.getContent();

        if ("kanban".equals(view)) {
            return BoardResponse.kanban(view, items.stream()
                    .collect(Collectors.groupingBy(i -> i.getStage() != null ? i.getStage() : "未知")));
        }
        return BoardResponse.table(view, items, result.getTotalElements());
    }

    private String mapSortField(String sort) {
        return switch (sort != null ? sort : "updatedAt") {
            case "amount" -> "amountWan";
            case "name" -> "name";
            case "code" -> "code";
            default -> "updatedAt";
        };
    }
}

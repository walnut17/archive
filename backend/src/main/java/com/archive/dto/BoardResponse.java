package com.archive.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 项目看板响应.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BoardResponse {

    private String view;
    private List<ProjectBoardItem> items;
    private Map<String, List<ProjectBoardItem>> kanban;
    private long total;

    public static BoardResponse table(String view, List<ProjectBoardItem> items, long total) {
        return new BoardResponse(view, items, null, total);
    }

    public static BoardResponse kanban(String view, Map<String, List<ProjectBoardItem>> groups) {
        long total = groups.values().stream().mapToLong(List::size).sum();
        return new BoardResponse(view, null, groups, total);
    }
}

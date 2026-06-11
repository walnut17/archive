package com.archive.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 项目看板行 DTO.
 */
@Data
@NoArgsConstructor
public class ProjectBoardItem {

    private Long id;
    private String code;
    private String name;
    private String region;
    private String stage;
    private Long amount;
    private Long proposalCount;
    private Long todoCount;
    private LocalDateTime lastUpdated;
    private Boolean masked;

    /** JPQL constructor projection. */
    public ProjectBoardItem(Long id, String code, String name, String region, String stage,
                            Long amount, Long proposalCount, Long todoCount,
                            LocalDateTime lastUpdated, Boolean masked) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.region = region;
        this.stage = stage;
        this.amount = amount;
        this.proposalCount = proposalCount;
        this.todoCount = todoCount;
        this.lastUpdated = lastUpdated;
        this.masked = masked;
    }
}

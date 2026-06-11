package com.archive.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 事实变更对比 DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FactEventDiff {

    private String before;
    private String after;
    private String evidenceSnippet;
}

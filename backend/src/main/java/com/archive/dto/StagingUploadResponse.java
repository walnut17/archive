package com.archive.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StagingUploadResponse {
    private Long draftProjectId;
    private String draftProjectCode;
    private Long proposalId;
    private Long materialId;
    private Long materialVersionId;
}

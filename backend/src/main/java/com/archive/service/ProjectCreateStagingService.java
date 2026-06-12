package com.archive.service;

import com.archive.dto.ProposalRequest;
import com.archive.dto.ProjectRequest;
import com.archive.dto.StagingUploadResponse;
import com.archive.entity.Material;
import com.archive.entity.MaterialVersion;
import com.archive.entity.Proposal;
import com.archive.entity.Project;
import com.archive.repository.MaterialVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * RI-16: 立项上传优先 — 创建草稿项目链并上传首份材料.
 */
@Service
@RequiredArgsConstructor
public class ProjectCreateStagingService {

    private final ProjectService projectService;
    private final ProposalService proposalService;
    private final MaterialService materialService;
    private final MaterialVersionRepository materialVersionRepository;

    @Transactional
    public StagingUploadResponse stagingUpload(MultipartFile file, String uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String draftCode = "DRAFT-" + suffix;

        Project project = projectService.create(ProjectRequest.builder()
                .code(draftCode)
                .name("待填写项目名称")
                .category("其他")
                .status("草稿")
                .build());

        Proposal proposal = proposalService.create(ProposalRequest.builder()
                .code("PROP-" + suffix)
                .title("立项材料")
                .projectId(project.getId())
                .type("立项")
                .status("草稿")
                .build());

        List<Material> materials = materialService.batchUpload(
                proposal.getId(), new MultipartFile[]{file}, "其他", null, uploadedBy);

        Material material = materials.get(0);
        MaterialVersion version = materialVersionRepository
                .findFirstByMaterialIdOrderByVersionNoDesc(material.getId())
                .orElseThrow(() -> new IllegalStateException("上传后未找到材料版本"));

        return StagingUploadResponse.builder()
                .draftProjectId(project.getId())
                .draftProjectCode(draftCode)
                .proposalId(proposal.getId())
                .materialId(material.getId())
                .materialVersionId(version.getId())
                .build();
    }
}

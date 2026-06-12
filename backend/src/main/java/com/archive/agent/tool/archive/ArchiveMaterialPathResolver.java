package com.archive.agent.tool.archive;

import com.archive.entity.MaterialVersion;
import com.archive.repository.MaterialVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 根据 materialVersionId 解析文件存储路径.
 * 将 DB 中的存储路径转换为相对 file-root / parsed-root 的相对路径.
 */
@Component
public class ArchiveMaterialPathResolver {

    private static final Logger log = LoggerFactory.getLogger(ArchiveMaterialPathResolver.class);

    private final MaterialVersionRepository materialVersionRepo;

    public ArchiveMaterialPathResolver(MaterialVersionRepository materialVersionRepo) {
        this.materialVersionRepo = materialVersionRepo;
    }

    /**
     * 解析 materialVersionId 到文件存储的相对路径.
     *
     * @param materialVersionId 材料版本 ID
     * @param zone files / parsed
     * @return 相对 file-root / parsed-root 的路径
     */
    public Optional<String> resolve(Long materialVersionId, String zone) {
        Optional<MaterialVersion> mvOpt = materialVersionRepo.findById(materialVersionId);
        if (mvOpt.isEmpty()) {
            log.warn("[ArchiveMaterialPathResolver] materialVersion {} 不存在", materialVersionId);
            return Optional.empty();
        }

        MaterialVersion mv = mvOpt.get();
        // 构造相对路径: {material_id}/v{version_no}/{filename}
        String relativePath = switch (zone) {
            case "files" -> buildFilesPath(mv);
            case "parsed" -> buildParsedPath(mv);
            default -> null;
        };

        if (relativePath == null) {
            return Optional.empty();
        }

        return Optional.of(relativePath);
    }

    private String buildFilesPath(MaterialVersion mv) {
        // files 区路径: {proposal_id}/{material_id}/v{version_no}_{original_filename}
        Long materialId = mv.getMaterial() != null ? mv.getMaterial().getId() : 0;
        Long proposalId = 0;
        if (mv.getMaterial() != null && mv.getMaterial().getProposal() != null) {
            proposalId = mv.getMaterial().getProposal().getId();
        }
        String filename = mv.getOriginalFilename() != null ? mv.getOriginalFilename() : "unknown";
        return String.format("%d/%d/v%d_%s", proposalId, materialId, mv.getVersionNo(), filename);
    }

    private String buildParsedPath(MaterialVersion mv) {
        // parsed 区路径: {material_id}/v{version_no}_parsed.txt
        Long materialId = mv.getMaterial() != null ? mv.getMaterial().getId() : 0;
        return String.format("%d/v%d_parsed.txt", materialId, mv.getVersionNo());
    }
}

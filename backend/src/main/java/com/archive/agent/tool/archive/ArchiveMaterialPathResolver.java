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
        String relativePath = switch (zone) {
            case "files" -> mv.getStoragePath();
            case "parsed" -> mv.getParsedTextPath();
            default -> null;
        };

        if (relativePath == null || relativePath.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(relativePath);
    }
}

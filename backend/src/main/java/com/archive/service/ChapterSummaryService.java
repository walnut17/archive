package com.archive.service;

import com.archive.entity.ChapterSummary;
import com.archive.repository.ChapterSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * 章节摘要业务逻辑.
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterSummaryService {

    private final ChapterSummaryRepository chapterSummaryRepository;

    /**
     * 按材料版本 ID 查询所有章节摘要.
     */
    public List<ChapterSummary> getByMaterialVersionId(Long materialVersionId) {
        return chapterSummaryRepository.findByMaterialVersionId(materialVersionId);
    }

    /**
     * 按材料版本 ID 和章节号查询.
     */
    public Optional<ChapterSummary> getByMaterialVersionIdAndChapterNo(Long materialVersionId, Integer chapterNo) {
        return chapterSummaryRepository.findByMaterialVersionIdAndChapterNo(materialVersionId, chapterNo);
    }

    /**
     * 保存章节摘要.
     */
    @Transactional
    public ChapterSummary save(ChapterSummary chapterSummary) {
        if (chapterSummary.getMaterialVersionId() == null) {
            throw new IllegalArgumentException("材料版本 ID 不能为空");
        }
        if (chapterSummary.getChapterNo() == null) {
            throw new IllegalArgumentException("章节号不能为空");
        }
        return chapterSummaryRepository.save(chapterSummary);
    }

    /**
     * 按材料版本 ID 删除所有章节摘要.
     */
    @Transactional
    public void deleteByMaterialVersionId(Long materialVersionId) {
        chapterSummaryRepository.deleteByMaterialVersionId(materialVersionId);
        log.info("已删除材料版本 {} 的全部章节摘要", materialVersionId);
    }
}

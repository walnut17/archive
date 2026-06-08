package com.archive.repository;

import com.archive.entity.ChapterSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 章节摘要仓库.
 *
 * @author Mavis
 */
@Repository
public interface ChapterSummaryRepository extends JpaRepository<ChapterSummary, Long> {

    List<ChapterSummary> findByMaterialVersionId(Long materialVersionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ChapterSummary cs WHERE cs.materialVersionId = :materialVersionId")
    void deleteByMaterialVersionId(@Param("materialVersionId") Long materialVersionId);

    Optional<ChapterSummary> findByMaterialVersionIdAndChapterNo(Long materialVersionId, Integer chapterNo);
}

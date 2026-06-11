package com.archive.repository;

import com.archive.dto.ProjectBoardItem;
import com.archive.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 项目仓库.
 *
 * @author Mavis
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByCode(String code);

    boolean existsByCode(String code);

    Page<Project> findByStatus(String status, Pageable pageable);

    Page<Project> findByOwnerId(Long ownerId, Pageable pageable);

    @Query("SELECT p FROM Project p WHERE " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(p.summary) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY p.createdAt DESC")
    Page<Project> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT p.status, COUNT(p) FROM Project p GROUP BY p.status")
    List<Object[]> countByStatus();

    @Query(value = "SELECT code, name, customer_name, MATCH(name, customer_name) AGAINST(:q IN BOOLEAN MODE) AS score FROM project WHERE MATCH(name, customer_name) AGAINST(:q IN BOOLEAN MODE) ORDER BY score DESC LIMIT :topN", nativeQuery = true)
    List<Object[]> searchByNameOrCustomerFulltext(@Param("q") String q, @Param("topN") int topN);

    /**
     * LIKE 模糊兜底: 搜 name / code / summary / customer_name 四字段, 任意一个 LIKE 命中即返回.
     * 用于 FULLTEXT 漏掉的情况 (如简称、拼音首字母、错别字).
     * 优先级低于 FULLTEXT, 仅在 FULLTEXT 返回空时调用.
     */
    @Query("SELECT p FROM Project p WHERE " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR (p.summary IS NOT NULL AND LOWER(p.summary) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           " OR (p.customerName IS NOT NULL AND LOWER(p.customerName) LIKE LOWER(CONCAT('%', :keyword, '%')))) " +
           "ORDER BY p.createdAt DESC")
    List<Project> searchByKeywordAsList(@Param("keyword") String keyword, org.springframework.data.domain.Pageable pageable);

    @Query(value = """
            SELECT new com.archive.dto.ProjectBoardItem(
                p.id, p.code, p.name, p.category, p.status, p.amountWan,
                (SELECT COUNT(pr.id) FROM Proposal pr WHERE pr.projectId = p.id),
                (SELECT COUNT(t.id) FROM Todo t WHERE t.projectId = p.id AND t.status = 'pending'),
                p.updatedAt, false
            )
            FROM Project p
            WHERE (:region IS NULL OR :region = '' OR p.category = :region)
              AND (:stage IS NULL OR :stage = '' OR p.status = :stage)
            """,
            countQuery = """
            SELECT COUNT(p) FROM Project p
            WHERE (:region IS NULL OR :region = '' OR p.category = :region)
              AND (:stage IS NULL OR :stage = '' OR p.status = :stage)
            """)
    Page<ProjectBoardItem> findBoardView(
            @Param("region") String region,
            @Param("stage") String stage,
            Pageable pageable);
}

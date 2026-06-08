package com.archive.repository;

import com.archive.entity.Material;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 材料仓库.
 *
 * @author Mavis
 */
@Repository
public interface MaterialRepository extends JpaRepository<Material, Long> {

    List<Material> findByProposalId(Long proposalId);

    Page<Material> findByProposalId(Long proposalId, Pageable pageable);

    List<Material> findByCategory(String category);

    Page<Material> findByStatus(String status, Pageable pageable);

    long countByProposalId(Long proposalId);

    long countByCategory(String category);

    @Query("SELECT m FROM Material m WHERE " +
           "LOWER(m.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY m.createdAt DESC")
    Page<Material> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}

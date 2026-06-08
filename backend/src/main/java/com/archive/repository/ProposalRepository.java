package com.archive.repository;

import com.archive.entity.Proposal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 议案仓库.
 *
 * @author Mavis
 */
@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Long> {

    Optional<Proposal> findByCode(String code);

    boolean existsByCode(String code);

    List<Proposal> findByProjectId(Long projectId);

    Page<Proposal> findByProjectId(Long projectId, Pageable pageable);

    Page<Proposal> findByStatus(String status, Pageable pageable);

    @Query("SELECT p FROM Proposal p WHERE " +
           "(LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(p.summary) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY p.createdAt DESC")
    Page<Proposal> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    long countByProjectId(Long projectId);

    long countByStatus(String status);
}

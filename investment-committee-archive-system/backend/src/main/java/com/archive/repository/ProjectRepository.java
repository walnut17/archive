package com.archive.repository;

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
}

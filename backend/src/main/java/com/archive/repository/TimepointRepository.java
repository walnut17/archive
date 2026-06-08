package com.archive.repository;

import com.archive.entity.Timepoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 时间节点仓库.
 *
 * @author Mavis
 */
@Repository
public interface TimepointRepository extends JpaRepository<Timepoint, Long> {

    Page<Timepoint> findByProjectId(Long projectId, Pageable pageable);

    List<Timepoint> findByDueAtBetween(LocalDate start, LocalDate end);

    List<Timepoint> findByStatus(String status);

    Page<Timepoint> findByOwnerId(Long ownerId, Pageable pageable);

    long countByStatusAndDueAtBefore(String status, LocalDate date);
}

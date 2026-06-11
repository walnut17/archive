package com.archive.repository;

import com.archive.entity.ProjectFactEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 关键事实事件仓库.
 */
@Repository
public interface ProjectFactEventRepository extends JpaRepository<ProjectFactEvent, Long> {

    List<ProjectFactEvent> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    Optional<ProjectFactEvent> findTopByProjectIdAndFactTypeAndCreatedAtBeforeAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long projectId, String factType, LocalDateTime before);
}

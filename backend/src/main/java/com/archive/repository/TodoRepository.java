package com.archive.repository;

import com.archive.entity.Todo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 待办事项仓库.
 *
 * @author Mavis
 */
@Repository
public interface TodoRepository extends JpaRepository<Todo, Long> {

    Page<Todo> findByOwnerId(Long ownerId, Pageable pageable);

    Page<Todo> findByStatus(String status, Pageable pageable);

    Page<Todo> findByProjectId(Long projectId, Pageable pageable);

    Page<Todo> findByOwnerIdAndStatus(Long ownerId, String status, Pageable pageable);

    List<Todo> findBySourceAndSourceRefId(String source, Long sourceRefId);

    long countByStatus(String status);

    long countByProjectIdAndStatus(Long projectId, String status);
}

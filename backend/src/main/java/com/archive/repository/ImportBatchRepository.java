package com.archive.repository;

import com.archive.entity.ImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 导入批次仓库.
 */
@Repository
public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
}

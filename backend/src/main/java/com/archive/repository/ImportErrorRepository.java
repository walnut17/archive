package com.archive.repository;

import com.archive.entity.ImportError;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 导入错误仓库.
 */
@Repository
public interface ImportErrorRepository extends JpaRepository<ImportError, Long> {

    List<ImportError> findByBatchIdOrderByRowAsc(Long batchId);
}

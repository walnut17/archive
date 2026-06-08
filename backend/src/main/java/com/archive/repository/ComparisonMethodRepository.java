package com.archive.repository;

import com.archive.entity.ComparisonMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 对比方法仓库.
 *
 * @author Mavis
 */
@Repository
public interface ComparisonMethodRepository extends JpaRepository<ComparisonMethod, Long> {

    Optional<ComparisonMethod> findByCode(String code);

    List<ComparisonMethod> findByFromTypeAndToTypeAndEnabled(String fromType, String toType, Boolean enabled);

    List<ComparisonMethod> findByEnabledOrderBySortOrderAsc(Boolean enabled);
}

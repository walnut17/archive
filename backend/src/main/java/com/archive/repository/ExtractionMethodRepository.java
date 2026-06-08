package com.archive.repository;

import com.archive.entity.ExtractionMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 抽取方法仓库.
 *
 * @author Mavis
 */
@Repository
public interface ExtractionMethodRepository extends JpaRepository<ExtractionMethod, Long> {

    Optional<ExtractionMethod> findByCode(String code);

    List<ExtractionMethod> findByApplyToAndEnabled(String applyTo, Boolean enabled);

    List<ExtractionMethod> findByEnabledOrderBySortOrderAsc(Boolean enabled);
}

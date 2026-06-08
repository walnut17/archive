package com.archive.repository;

import com.archive.entity.DictType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 字典类型仓库.
 *
 * @author Mavis
 */
@Repository
public interface DictTypeRepository extends JpaRepository<DictType, Long> {

    Optional<DictType> findByTypeCode(String typeCode);

    List<DictType> findByEnabledOrderBySortOrderAsc(Boolean enabled);

    boolean existsByTypeCode(String typeCode);
}

package com.archive.repository;

import com.archive.entity.DictItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 字典条目仓库.
 *
 * @author Mavis
 */
@Repository
public interface DictItemRepository extends JpaRepository<DictItem, Long> {

    List<DictItem> findByTypeCodeOrderBySortOrderAsc(String typeCode);

    List<DictItem> findByTypeCodeAndEnabledOrderBySortOrderAsc(String typeCode, Boolean enabled);

    Optional<DictItem> findByTypeCodeAndItemKey(String typeCode, String itemKey);

    List<DictItem> findByTypeCodeAndIsDefault(String typeCode, Boolean isDefault);
}

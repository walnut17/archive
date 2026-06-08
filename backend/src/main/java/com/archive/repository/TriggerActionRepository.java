package com.archive.repository;

import com.archive.entity.TriggerAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 触发动作仓库.
 *
 * @author Mavis
 */
@Repository
public interface TriggerActionRepository extends JpaRepository<TriggerAction, Long> {

    List<TriggerAction> findByRuleIdOrderBySortOrderAsc(Long ruleId);

    List<TriggerAction> findByRuleIdAndEnabled(Long ruleId, Boolean enabled);
}

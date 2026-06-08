package com.archive.repository;

import com.archive.entity.TriggerRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 触发规则仓库.
 *
 * @author Mavis
 */
@Repository
public interface TriggerRuleRepository extends JpaRepository<TriggerRule, Long> {

    Optional<TriggerRule> findByCode(String code);

    List<TriggerRule> findByTriggerEventAndEnabled(String triggerEvent, Boolean enabled);

    List<TriggerRule> findByEnabledOrderByPriorityDesc(Boolean enabled);

    boolean existsByCode(String code);
}

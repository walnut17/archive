package com.archive.service;

import com.archive.dto.PageResponse;
import com.archive.entity.TriggerAction;
import com.archive.entity.TriggerRule;
import com.archive.repository.TriggerActionRepository;
import com.archive.repository.TriggerRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 触发规则业务逻辑.
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriggerRuleService {

    private final TriggerRuleRepository triggerRuleRepository;
    private final TriggerActionRepository triggerActionRepository;

    /**
     * 创建规则及关联动作.
     *
     * @param rule    规则实体
     * @param actions 关联动作列表
     * @return 已保存的规则
     */
    @Transactional
    public TriggerRule create(TriggerRule rule, List<TriggerAction> actions) {
        if (rule.getCode() == null || rule.getCode().isBlank()) {
            throw new IllegalArgumentException("规则代码不能为空");
        }
        if (triggerRuleRepository.existsByCode(rule.getCode())) {
            throw new IllegalArgumentException("规则代码已存在: " + rule.getCode());
        }
        if (rule.getName() == null || rule.getName().isBlank()) {
            throw new IllegalArgumentException("规则名称不能为空");
        }
        if (rule.getTriggerEvent() == null || rule.getTriggerEvent().isBlank()) {
            throw new IllegalArgumentException("触发事件不能为空");
        }
        if (rule.getTriggerCondition() == null || rule.getTriggerCondition().isBlank()) {
            throw new IllegalArgumentException("触发条件不能为空");
        }

        TriggerRule saved = triggerRuleRepository.save(rule);

        if (actions != null && !actions.isEmpty()) {
            for (TriggerAction action : actions) {
                action.setRuleId(saved.getId());
                triggerActionRepository.save(action);
            }
        }

        log.info("已创建触发规则 id={}, code={}", saved.getId(), saved.getCode());
        return saved;
    }

    /**
     * 更新规则及关联动作(全量替换).
     *
     * @param id      规则 ID
     * @param rule    规则新数据
     * @param actions 新动作列表
     * @return 已更新的规则
     */
    @Transactional
    public TriggerRule update(Long id, TriggerRule rule, List<TriggerAction> actions) {
        TriggerRule existing = triggerRuleRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("触发规则不存在: id=" + id));

        // 如果改了 code,检查唯一性
        if (rule.getCode() != null && !rule.getCode().equals(existing.getCode())) {
            if (triggerRuleRepository.existsByCode(rule.getCode())) {
                throw new IllegalArgumentException("规则代码已存在: " + rule.getCode());
            }
            existing.setCode(rule.getCode());
        }
        if (rule.getName() != null) {
            existing.setName(rule.getName());
        }
        if (rule.getDescription() != null) {
            existing.setDescription(rule.getDescription());
        }
        if (rule.getTriggerEvent() != null) {
            existing.setTriggerEvent(rule.getTriggerEvent());
        }
        if (rule.getTriggerCondition() != null) {
            existing.setTriggerCondition(rule.getTriggerCondition());
        }
        if (rule.getEnabled() != null) {
            existing.setEnabled(rule.getEnabled());
        }
        if (rule.getPriority() != null) {
            existing.setPriority(rule.getPriority());
        }

        TriggerRule saved = triggerRuleRepository.save(existing);

        // 全量替换动作:删旧 → 插新
        List<TriggerAction> oldActions = triggerActionRepository.findByRuleIdOrderBySortOrderAsc(id);
        if (oldActions != null) {
            triggerActionRepository.deleteAll(oldActions);
        }
        if (actions != null && !actions.isEmpty()) {
            for (TriggerAction action : actions) {
                action.setId(null);
                action.setRuleId(saved.getId());
                triggerActionRepository.save(action);
            }
        }

        log.info("已更新触发规则 id={}", id);
        return saved;
    }

    /**
     * 删除规则(级联删除关联动作).
     */
    @Transactional
    public void delete(Long id) {
        TriggerRule existing = triggerRuleRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("触发规则不存在: id=" + id));

        List<TriggerAction> actions = triggerActionRepository.findByRuleIdOrderBySortOrderAsc(id);
        if (actions != null && !actions.isEmpty()) {
            triggerActionRepository.deleteAll(actions);
        }
        triggerRuleRepository.delete(existing);
        log.info("已删除触发规则 id={} 及其关联动作", id);
    }

    /**
     * 按 ID 查询规则.
     */
    public TriggerRule getById(Long id) {
        return triggerRuleRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("触发规则不存在: id=" + id));
    }

    /**
     * 按代码查询规则.
     */
    public TriggerRule getByCode(String code) {
        return triggerRuleRepository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("触发规则不存在: code=" + code));
    }

    /**
     * 分页查询所有规则.
     */
    public PageResponse<TriggerRule> listAll(int page, int size) {
        Page<TriggerRule> result = triggerRuleRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "priority", "createdAt")));
        return PageResponse.of(result);
    }

    /**
     * 按事件查询已启用的规则及其动作.
     */
    public List<TriggerRule> getEnabledByEvent(String triggerEvent) {
        if (triggerEvent == null || triggerEvent.isBlank()) {
            throw new IllegalArgumentException("触发事件不能为空");
        }
        return triggerRuleRepository.findByTriggerEventAndEnabled(triggerEvent, true);
    }

    /**
     * 查询规则下的所有动作.
     */
    public List<TriggerAction> getActionsByRule(Long ruleId) {
        if (!triggerRuleRepository.existsById(ruleId)) {
            throw new NoSuchElementException("触发规则不存在: id=" + ruleId);
        }
        return triggerActionRepository.findByRuleIdOrderBySortOrderAsc(ruleId);
    }
}

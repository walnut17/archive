package com.archive.engine;

import com.archive.entity.TriggerAction;
import com.archive.entity.TriggerRule;
import com.archive.repository.TriggerActionRepository;
import com.archive.repository.TriggerRuleRepository;
import com.archive.service.AuditLogService;
import com.archive.service.TodoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 触发规则引擎 — 监听业务事件并评估触发规则.
 *
 * <p>当业务事件发生时(MaterialUploadedEvent / MaterialCategorizedEvent /
 * ProposalStatusChangedEvent / TimepointApproachingEvent),加载已启用的规则,
 * 用 {@link SimpleExpressionEvaluator} 评估条件表达式,匹配后执行关联动作。
 *
 * <p>动作类型支持:
 * <ul>
 *   <li>create_todo — 创建待办事项</li>
 *   <li>send_notification — 发送通知(预留)</li>
 * </ul>
 *
 * @author Mavis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriggerEngine {

    private final TriggerRuleRepository triggerRuleRepository;
    private final TriggerActionRepository triggerActionRepository;
    private final TodoService todoService;
    private final AuditLogService auditLogService;

    // ========== Events ==========

    /**
     * 材料上传事件.
     */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class MaterialUploadedEvent {
        private final Long materialId;
        private final String materialTitle;
        private final String category;
        private final Long proposalId;
    }

    /**
     * 材料分类事件.
     */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class MaterialCategorizedEvent {
        private final Long materialId;
        private final String materialTitle;
        private final String category;
    }

    /**
     * 议案状态变更事件.
     */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class ProposalStatusChangedEvent {
        private final Long proposalId;
        private final String newStatus;
        private final String oldStatus;
    }

    /**
     * 时点临近事件.
     */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class TimepointApproachingEvent {
        private final Long timepointId;
        private final String name;
        private final int daysToDue;
        private final String ownerRole;
    }

    // ========== Event Listeners ==========

    @EventListener
    public void handleMaterialUploaded(MaterialUploadedEvent event) {
        Map<String, Object> ctx = Map.of(
                "event.material.id", event.getMaterialId(),
                "event.material.title", event.getMaterialTitle(),
                "event.material.category", event.getCategory(),
                "event.material.proposalId", event.getProposalId()
        );
        evaluate("MaterialUploadedEvent", ctx);
    }

    @EventListener
    public void handleMaterialCategorized(MaterialCategorizedEvent event) {
        Map<String, Object> ctx = Map.of(
                "event.material.id", event.getMaterialId(),
                "event.material.title", event.getMaterialTitle(),
                "event.material.category", event.getCategory()
        );
        evaluate("MaterialCategorizedEvent", ctx);
    }

    @EventListener
    public void handleProposalStatusChanged(ProposalStatusChangedEvent event) {
        Map<String, Object> ctx = Map.of(
                "event.proposal.id", event.getProposalId(),
                "event.proposal.newStatus", event.getNewStatus(),
                "event.proposal.oldStatus", event.getOldStatus()
        );
        evaluate("ProposalStatusChangedEvent", ctx);
    }

    @EventListener
    public void handleTimepointApproaching(TimepointApproachingEvent event) {
        Map<String, Object> ctx = Map.of(
                "event.timepoint.id", event.getTimepointId(),
                "event.timepoint.name", event.getName(),
                "event.timepoint.daysToDue", event.getDaysToDue(),
                "event.timepoint.ownerRole", event.getOwnerRole()
        );
        evaluate("TimepointApproachingEvent", ctx);
    }

    // ========== Core Evaluation ==========

    /**
     * 评估指定事件类型的规则并执行匹配动作.
     *
     * @param triggerEvent 事件类型名(对应 TriggerRule.triggerEvent)
     * @param eventContext 事件上下文字典,Key 为 {@code event.<type>.<field>} 格式
     */
    @Async("taskExecutor")
    public void evaluate(String triggerEvent, Map<String, Object> eventContext) {
        try {
            // 1. 加载已启用的规则
            List<TriggerRule> rules = triggerRuleRepository
                    .findByTriggerEventAndEnabled(triggerEvent, true);

            if (rules.isEmpty()) {
                log.debug("No enabled rules for event: {}", triggerEvent);
                return;
            }

            SimpleExpressionEvaluator evaluator = new SimpleExpressionEvaluator(eventContext);

            for (TriggerRule rule : rules) {
                try {
                    // 2. 评估条件
                    boolean matched = evaluator.evaluate(rule.getTriggerCondition());
                    if (!matched) {
                        continue;
                    }

                    // 3. 加载并执行动作
                    List<TriggerAction> actions = triggerActionRepository
                            .findByRuleIdAndEnabled(rule.getId(), true);

                    for (TriggerAction action : actions) {
                        executeAction(action, eventContext);
                    }

                    // 4. 更新规则统计
                    rule.setLastRunAt(LocalDateTime.now());
                    rule.setLastMatchCount(rule.getLastMatchCount() != null
                            ? rule.getLastMatchCount() + 1 : 1);
                    triggerRuleRepository.save(rule);

                    // 5. 审计日志
                    auditLogService.log("rule_fire", "trigger_rule", rule.getId(),
                            "事件: " + triggerEvent + ", 条件: " + rule.getTriggerCondition(),
                            null);

                    log.info("Rule [{}] matched for event [{}]", rule.getCode(), triggerEvent);

                } catch (Exception e) {
                    log.warn("Rule evaluation failed for rule [{}]: {}", rule.getCode(), e.getMessage());
                }
            }

        } catch (Exception e) {
            log.warn("TriggerEngine.evaluate failed for event [{}]: {}", triggerEvent, e.getMessage());
        }
    }

    /**
     * 执行单个动作.
     */
    private void executeAction(TriggerAction action, Map<String, Object> eventContext) {
        try {
            switch (action.getActionType()) {
                case "create_todo":
                    todoService.createFromTrigger(action.getActionTemplate(), eventContext);
                    break;
                case "send_notification":
                    log.info("Notification action not yet implemented: ruleId={}", action.getRuleId());
                    break;
                default:
                    log.warn("Unknown action type: {}", action.getActionType());
            }
        } catch (Exception e) {
            log.warn("Action execution failed for actionId={}, type={}: {}",
                    action.getId(), action.getActionType(), e.getMessage());
        }
    }

    // ========== SimpleExpressionEvaluator ==========

    /**
     * 简单条件表达式求值器.
     *
     * <p>支持操作符: ==, !=, contains, &&, ||.
     * 字段引用格式: {@code event.material.category}(点号分隔从 eventContext 取值).
     *
     * <p>示例:
     * <ul>
     *   <li>{@code event.material.category == '收款凭证'}</li>
     *   <li>{@code event.proposal.newStatus != '草稿'}</li>
     *   <li>{@code event.material.title contains '年度'}</li>
     *   <li>{@code event.material.category == '尽调报告' && event.proposal.oldStatus == '草稿'}</li>
     * </ul>
     */
    public static class SimpleExpressionEvaluator {

        private final Map<String, Object> context;

        public SimpleExpressionEvaluator(Map<String, Object> context) {
            this.context = context;
        }

        /**
         * 求值单个条件表达式.
         *
         * @param expression 条件表达式
         * @return 求值结果
         */
        public boolean evaluate(String expression) {
            if (expression == null || expression.isBlank()) {
                return true; // 无条件视为匹配
            }

            String trimmed = expression.trim();

            // 处理 ||
            int orIndex = findTopLevelOperator(trimmed, "||");
            if (orIndex >= 0) {
                String left = trimmed.substring(0, orIndex).trim();
                String right = trimmed.substring(orIndex + 2).trim();
                return evaluate(left) || evaluate(right);
            }

            // 处理 &&
            int andIndex = findTopLevelOperator(trimmed, "&&");
            if (andIndex >= 0) {
                String left = trimmed.substring(0, andIndex).trim();
                String right = trimmed.substring(andIndex + 2).trim();
                return evaluate(left) && evaluate(right);
            }

            // 处理 contains
            int containsIndex = trimmed.indexOf(" contains ");
            if (containsIndex >= 0) {
                String field = trimmed.substring(0, containsIndex).trim();
                String expected = extractStringLiteral(trimmed.substring(containsIndex + 10).trim());
                String actual = resolveField(field);
                return actual != null && actual.contains(expected);
            }

            // 处理 !=
            int neIndex = trimmed.indexOf(" != ");
            if (neIndex >= 0) {
                String field = trimmed.substring(0, neIndex).trim();
                String expected = extractStringLiteral(trimmed.substring(neIndex + 4).trim());
                String actual = resolveField(field);
                if (actual == null) return expected == null || "null".equals(expected);
                return !actual.equals(expected);
            }

            // 处理 ==
            int eqIndex = trimmed.indexOf(" == ");
            if (eqIndex >= 0) {
                String field = trimmed.substring(0, eqIndex).trim();
                String expected = extractStringLiteral(trimmed.substring(eqIndex + 4).trim());
                String actual = resolveField(field);
                if (actual == null) return expected == null || "null".equals(expected);
                return actual.equals(expected);
            }

            log.warn("Unsupported expression: {}", expression);
            return false;
        }

        /**
         * 在顶层(不在引号内)查找操作符.
         */
        private int findTopLevelOperator(String expr, String op) {
            int quoteDepth = 0;
            for (int i = 0; i <= expr.length() - op.length(); i++) {
                char c = expr.charAt(i);
                if (c == '\'') quoteDepth ^= 1;
                if (quoteDepth == 0 && expr.substring(i).startsWith(op)) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * 提取字符串字面量(去掉首尾单引号).
         */
        private String extractStringLiteral(String s) {
            if (s == null) return "";
            s = s.trim();
            if (s.startsWith("'") && s.endsWith("'") && s.length() >= 2) {
                return s.substring(1, s.length() - 1);
            }
            return s;
        }

        /**
         * 用点号路径从 context 取值.
         */
        private String resolveField(String fieldPath) {
            if (fieldPath == null || fieldPath.isBlank()) return null;
            Object value = context.get(fieldPath);
            if (value == null) return null;
            return String.valueOf(value);
        }
    }
}

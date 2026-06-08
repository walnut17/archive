package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 触发动作实体 — 规则的动作(1:N).
 *
 * 一条触发规则(TriggerRule)可以配置多个动作,
 * 按 sort_order 顺序依次执行。
 * actionTemplate 为 JSON 格式,不同 actionType 的参数结构各不相同。
 *
 * @author Mavis
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "trigger_action", indexes = {
        @Index(name = "idx_ta_rule", columnList = "rule_id"),
        @Index(name = "idx_ta_rule_sort", columnList = "rule_id, sort_order")
})
public class TriggerAction extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属规则 ID. */
    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    /** 动作类型:create_todo/send_notification. */
    @Column(name = "action_type", nullable = false, length = 32)
    private String actionType;

    /** 动作模板 JSON:{"todo_name":"...","due_days":3,"owner_role":"finance"}. */
    @Column(name = "action_template", nullable = false, columnDefinition = "JSON")
    private String actionTemplate;

    /** 执行顺序. */
    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 1;

    /** 是否启用. */
    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;
}

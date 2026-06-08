package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * 触发规则实体 — 规则引擎主表.
 *
 * 定义在特定事件(如材料上传、议案状态变更)发生时,
 * 通过 triggerCondition 表达式判断是否满足条件,
 * 满足时触发关联的 TriggerAction(如创建待办、发送通知)。
 *
 * @author Mavis
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "trigger_rule", indexes = {
        @Index(name = "idx_tr_code", columnList = "code"),
        @Index(name = "idx_tr_event_enabled", columnList = "trigger_event, enabled")
})
public class TriggerRule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 规则代码(全局唯一). */
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    /** 规则名称. */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** 规则说明. */
    @Column(name = "description", length = 1000)
    private String description;

    /** 触发事件:MaterialUploadedEvent/MaterialCategorizedEvent/ProposalStatusChangedEvent/TimepointApproachingEvent. */
    @Column(name = "trigger_event", nullable = false, length = 64)
    private String triggerEvent;

    /** 触发条件表达式:event.material.category == '收款凭证'. */
    @Column(name = "trigger_condition", nullable = false, length = 1000)
    private String triggerCondition;

    /** 是否启用. */
    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

    /** 是否系统内置(内置不可删). */
    @Column(name = "builtin")
    @Builder.Default
    private Boolean builtin = false;

    /** 评估优先级 1-5. */
    @Column(name = "priority")
    @Builder.Default
    private Integer priority = 3;

    /** 最近一次评估时间. */
    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    /** 最近一次命中数. */
    @Column(name = "last_match_count")
    @Builder.Default
    private Integer lastMatchCount = 0;
}

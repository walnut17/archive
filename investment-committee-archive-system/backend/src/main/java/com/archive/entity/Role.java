package com.archive.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 角色实体.
 *
 * 角色承载权限位,用户多对一挂角色。M0 阶段权限简化,role 字段直接是字符串(admin/committee/project_owner/employee),
 * 但保留独立 Role 实体便于未来扩展(权限位、ACL 等).
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "role")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 角色代码(唯一),如 admin / committee / project_owner / employee. */
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    /** 显示名称,如"管理员" / "投委会委员" / "项目经理" / "普通员工". */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** 描述. */
    @Column(name = "description", length = 512)
    private String description;

    /** 权限位(JSON 数组,存放具体权限标识,留待后续扩展). */
    @Column(name = "permissions", columnDefinition = "JSON")
    private String permissions;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

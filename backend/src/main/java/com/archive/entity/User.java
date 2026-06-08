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
 * 用户实体.
 *
 * @author Mavis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user", indexes = {
        @Index(name = "idx_username", columnList = "username", unique = true),
        @Index(name = "idx_role_id", columnList = "role_id")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录用户名(唯一). */
    @Column(name = "username", nullable = false, unique = true, length = 64)
    private String username;

    /** 显示名称. */
    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    /** BCrypt 加密后的密码(不要存明文). */
    @Column(name = "password_hash", nullable = false, length = 128)
    private String passwordHash;

    @Column(name = "email", length = 128)
    private String email;

    /** 角色 ID(多对一,实际权限以 role.permissions 为准). */
    @Column(name = "role_id")
    private Long roleId;

    /** 部门. */
    @Column(name = "department", length = 128)
    private String department;

    /** 在岗 / 停用. */
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private String status = "在岗";

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

# MOD-04 代码审查报告 — 业务功能新增

> 审查人：Sisyphus | 日期：2026-06-11 | 审查范围：MOD-04（43 文件，2557 行新增）
> 执行人：阿根廷 | 基线 commit：`5b9ea40`

---

## 0. 总体评价

**功能量最大的一个模块，前端组件质量参差不齐。** 后端的安全意识基本在线（白名单、参数化查询），但前端两个 XSS 漏洞比较明显（`v-html` 直出 DiffViewer 和 PreviewFrame）。MaskingService 也有一个逻辑 bug。

---

## 1. 🔴 严重问题（必须修）

### 1.1 `DiffViewer.vue:49` — v-html XSS

```vue
<div v-if="htmlDiff" class="diff-html" v-html="htmlDiff" />
```

`jsondiffpatch` 输出的 HTML 未经消毒直接渲染。如果 fact event 的 `before`/`after` 字段被注入了 `<script>alert(1)</script>`，就是存储型 XSS。

**修复**：用 `DOMPurify.sanitize(htmlDiff)` 消毒。

### 1.2 `PreviewFrame.vue:67` — Word 预览 XSS

```vue
<div v-else-if="isWord" class="word-preview" v-html="wordHtml" />
```

mammoth 将 docx 转为 HTML，同样未消毒。

**修复**：同 DiffViewer，用 DOMPurify 消毒。

---

## 2. 🟡 中等问题

### 2.1 `MaskingService.java:22,44-48` — unmaskRequestUrl 逻辑 bug

```java
resp.setCustomerName(project.getCustomerName());      // 行22: 先设原始值
resp.setMasked(true);
resp.setDisplayName(maskName(project.getCustomerName()));  // 行44
resp.setUnmaskRequestUrl("/api/projects/" + id + "/unmask-request");  // 行46: 总是设置
if (project.getCustomerName() != null) {
    resp.setCustomerName(maskName(project.getCustomerName()));  // 行48: 覆盖为脱敏值
}
```

问题：
1. 行 46 的 `unmaskRequestUrl` 无条件设置——即使 viewer 是 admin（不需要脱敏）或 viewerId=null（未登录），也会返回脱敏申请链接
2. 行 44 和行 48：先设 displayName 是脱敏值，又覆盖 customerName 为脱敏值，但 displayName 不应该和 customerName 一样

### 2.2 `NotificationController` — 缺少类级安全注解

```java
@RestController
@RequestMapping("/api/notifications")
public class NotificationController { ... }
```

虽然没有数据泄露风险（Service 层按 userId 过滤），但缺少显式授权注解是安全坏习惯。应该加：

```java
@PreAuthorize("isAuthenticated()")
```

### 2.3 `PreviewService` — 缺少材料访问权限检查

```java
public PreviewContent getForPreview(Long materialId, Integer version) {
    Material material = materialRepository.findById(materialId)...;
    // 未检查当前用户是否有权访问该 material
}
```

任何登录用户可预览任意材料。虽然"已知材料 ID"的门槛较高，但应当加上权限校验。

### 2.4 `Notification.vue:37` — router.push 开放重定向

```typescript
if (row.link) router.push(row.link)
```

`row.link` 来自服务端，如果被篡改可导致 open redirect。

**修复**：`if (row.link?.startsWith('/')) router.push(row.link)`

---

## 3. 🟢 轻微问题

### 3.1 `NotificationService.markAllRead()` — N+1 查询

```java
findByUserIdAndReadOrderByCreatedAtDesc(userId, false, Pageable.unpaged())
    .forEach(n -> { n.setRead(true); notificationRepository.save(n); });
```

未读通知多时会逐条 UPDATE。应改用 `@Modifying @Query` 批量更新。

### 3.2 `PreviewFrame.vue` — PDF iframe 无 sandbox

```vue
<iframe v-if="isPdf" :src="previewUrl" width="100%" height="600" />
```

缺少 `sandbox` 属性。PDF 在同源时可执行脚本。

**修复**：`<iframe sandbox="downloads" ... />`

### 3.3 `ImportController` — 上传文件大小无显式限制

无 `@Size` 或 `max-file-size` 配置引用。虽然 Spring Boot 默认 `1MB`，但如果项目改大了没有显式约束。

---

## 4. ✅ 正确的实现

| 模块 | 状态 | 说明 |
|------|------|------|
| 导入类型白名单 | ✅ | switch 白名单 + POI 安全解析 |
| 导出文件名 | ✅ | 白名单 + Content-Disposition 安全 |
| 数据脱敏（核心逻辑） | ✅ | committee 账号脱敏、admin 可见 |
| 通知轮询 | ✅ | 30s + store 生命周期管理 |
| ProjectBoard 排序白名单 | ✅ | mapSortField 限制安全字段 |
| 审批流程 unmask | ✅ | 审计日志 + 通知 admin |
| JPA 参数化查询 | ✅ | 全部 repos 确认 |
| 文件预览大小限制 | ✅ | 50MB 上限 |

---

## 5. 前端新增文件一览

| 文件 | 用途 | 质量 |
|------|------|------|
| `DiffViewer.vue` | 事实档案 diff 展示 | ⚠️ XSS（v-html） |
| `MaskedField.vue` | 脱敏字段组件 | ✅ 干净 |
| `NotifBell.vue` | 通知铃铛 | ✅ 干净 |
| `PreviewFrame.vue` | PDF/Word/图片预览 | ⚠️ XSS + iframe |
| `ImportWizard.vue` | 导入向导 | ✅ 干净 |
| `Notification.vue` | 通知列表 | ⚠️ router.push |
| `ProjectBoard.vue` | 项目看板 | ✅ 干净 |
| `store/notification.ts` | 通知状态管理 | ✅ 干净 |

---

*审查完。*

*审查人：Sisyphus*
*2 个 XSS（DiffViewer + PreviewFrame 的 v-html）、1 个逻辑 bug（MaskingService.unmaskRequestUrl）。前端整体风格统一，后端安全意识在线。*

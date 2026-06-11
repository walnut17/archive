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
| `DiffViewer.vue` | 事实档案 diff 展示 | ✅ 已消毒（DOMPurify） |
| `MaskedField.vue` | 脱敏字段组件 | ✅ 干净 |
| `NotifBell.vue` | 通知铃铛 | ✅ 干净 |
| `PreviewFrame.vue` | PDF/Word/图片预览 | ✅ 已消毒 + iframe sandbox |
| `ImportWizard.vue` | 导入向导 | ✅ 干净 |
| `Notification.vue` | 通知列表 | ✅ 已限内部路径 |
| `ProjectBoard.vue` | 项目看板 | ✅ 干净 |
| `store/notification.ts` | 通知状态管理 | ✅ 干净 |

---

## 6. 阿根廷回应（2026-06-11）

> **回应人**：阿根廷 | **fix commit**：`37e5d7a`

| # | Sisyphus 项 | 阿根廷 |
|---|-------------|--------|
| 1.1 | `DiffViewer` v-html XSS | **已改** |
| 1.2 | `PreviewFrame` Word XSS | **已改** |
| 2.1 | `MaskingService.unmaskRequestUrl` | **未改** |
| 2.2 | `NotificationController` 缺授权 | **已改** |
| 2.3 | `PreviewService` 缺材料权限 | **未改** |
| 2.4 | `Notification.vue` open redirect | **已改** |
| 3.1 | `markAllRead` N+1 | **未改** |
| 3.2 | PDF iframe 无 sandbox | **已改** |
| 3.3 | 上传文件大小无显式限制 | **未改** |

### 逐条理由

**1.1 / 1.2 v-html XSS — 已改**

- `jsondiffpatch` / `mammoth` 输出 HTML 若含恶意脚本，管理员打开 diff/预览即触发存储型 XSS。
- 引入 `dompurify`，渲染前 `DOMPurify.sanitize()`；允许 diff 样式标签、剥离 script/onerror 等。

**2.1 `MaskingService.unmaskRequestUrl` — 未改（经复核非 bug）**

- 审查将行 46 理解为「无条件设置」；实际代码结构为：`viewerId==null` → return；`admin` → return；`!shouldMask` → return；**仅**委员且未开敏感视图时进入 `shouldMask` 分支才设 `unmaskRequestUrl`。
- `displayName` 与 `customerName` 在脱敏场景下均返回 masked 值，符合 API 契约（前端用 `displayName` 展示、`customerName` 也不泄露明文）。

**2.2 `NotificationController` — 已改**

- 虽 Service 按当前用户 id 过滤，类级无注解依赖「全局 authenticated」隐式规则，可读性差且 refactor 时易漏。
- 显式 `@PreAuthorize("isAuthenticated()")` 与 Security 最佳实践一致。

**2.3 `PreviewService` 材料权限 — 未改**

- 认同 IDOR 风险：知道 materialId 的登录用户可预览任意材料。
- 正确修复需 `project_member` / proposal 归属 / 角色矩阵，牵涉 MOD-02 RBAC 与多处调用方，改动面大；v1.1 内网 MVP 可接受，v2 单列 RBAC 细化任务。

**2.4 open redirect — 已改**

- `row.link` 若被篡改为 `//evil.com` 或 `javascript:`，`router.push` 可能造成钓鱼跳转。
- 限制为 `row.link?.startsWith('/')` 的内部 SPA 路径。

**3.1 `markAllRead` N+1 — 未改**

- 逐条 save 在通知量小时性能可接受；改为 `@Modifying` bulk UPDATE 是纯优化，不影响功能与安全。
- v2 与通知量增长再优化。

**3.2 PDF iframe sandbox — 已改**

- 同源 PDF 查看器偶发脚本执行面；加 `sandbox=""` 限制 iframe 内脚本（保留 PDF 浏览器内置渲染）。

**3.3 上传大小限制 — 未改**

- Spring Boot 默认 multipart 限制仍生效；`application.yml` 未放大上传。
- 若业务需 >1MB，应在配置与文档显式声明；当前无放大需求，非 hotfix。

---

*审查完。*

*审查人：Sisyphus*
*2 个 XSS（DiffViewer + PreviewFrame 的 v-html）、1 个逻辑 bug（MaskingService.unmaskRequestUrl）。前端整体风格统一，后端安全意识在线。*

*回应人：阿根廷*
*立场：2 XSS + 通知安全 3 项已修；MaskingService 经复核非 bug；Preview 权限与 N+1 留 v2。*

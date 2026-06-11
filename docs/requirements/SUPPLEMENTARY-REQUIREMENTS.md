# 投委会档案管理系统 — 补充开发需求

> 基线代码: M1 完成（详见 [`test/old/M1-README.md`](../../test/old/M1-README.md)）
> 目标: 修复审计发现的 P0~P1 缺陷 + 补齐缺失模块 + 增加增强功能
> 使用对象: 开发 Agent（可读此文档后按 Phase 顺序执行）
> 关联文档: `REVIEW-REPORT.md`（问题清单）

---

## Phase 0 — 阻塞性缺陷修复（必须先修）

### P0-1：前端数据解包错误（7 处）

**问题**：`api/http.ts` 的 `getData()` 已从 Axios 响应中解包 `response.data.data`，但视图组件仍对返回值访问 `.data`，导致数据为 `undefined`。

**涉及文件**：

| 文件 | 行 | 错误代码 | 修正代码 |
|------|----|----------|---------|
| `frontend/src/views/ProjectForm.vue` | 31-32 | `const resp = await getProject(Number(id)); form.value = resp.data` | `form.value = await getProject(Number(id))` |
| `frontend/src/views/ProjectDetail.vue` | 37 | `project.value = (p as any).data` | `project.value = p` |
| `frontend/src/views/ProjectDetail.vue` | 38 | `proposals.value = ((pl as any).data.content) \|\| []` | `proposals.value = (pl).content \|\| []` |
| `frontend/src/views/ProposalDetail.vue` | 44 | `proposal.value = (p as any).data` | `proposal.value = p` |
| `frontend/src/views/ProposalDetail.vue` | 45 | `materials.value = ((ms as any).data.content) \|\| []` | `materials.value = (ms).content \|\| []` |
| `frontend/src/views/ProposalDetail.vue` | 93-94 | `const resp = await listVersions(m.id!); versions.value = resp.data` | `versions.value = await listVersions(m.id!)` |
| `frontend/src/views/ProposalDetail.vue` | 139-140 | `const resp = await listSections(...); sections.value = resp.data` | `sections.value = await listSections(...)` |

**验收标准**：打开项目详情、议案详情、编辑项目表单，各字段正常显示数据。

---

### P0-2：FULLTEXT 索引缺失

**问题**：`KnowledgeSearchService.search()` 对 `material_version.parsed_text` 执行 `MATCH ... AGAINST`，但 `init.sql` 未在该列上创建 FULLTEXT 索引。虽已有 `M2-fulltext-index.sql`，但未集成到初始化流程。

**要求**：
1. 修改 `backend/src/main/resources/db/init.sql`，在 `material_version` 表定义中增加：
   - `parsed_text LONGTEXT` 列（位于 `parse_error` 之后）
   - `FULLTEXT INDEX ft_parsed_text (parsed_text) WITH PARSER ngram`
2. 在 `init.sql` 末尾添加迁移检查 SQL，确保索引存在

**验收标准**：执行 `init.sql` 后，`/api/qa/ask` 接口能正常返回检索结果（无需二次执行 M2 迁移脚本）。

---

### P0-3：分页页码 0-based vs 1-based 偏移

**问题**：后端 Spring Data `Pageable` 使用 0-based 页码，前端 Element Plus `<el-pagination>` 使用 1-based 页码，导致分页交互后数据错位。

**涉及文件**：
- `frontend/src/views/ProjectList.vue`

**要求**：
1. 将 `query.page` 初始值改为 `1`
2. 调用 `listProjects()` 时传 `page: query.value.page - 1`
3. 修改 `fetch()` 方法中的查询参数构造

**验收标准**：点击分页第 2 页 → 正确显示第 2 页数据（而非第 3 页）。

---

### P0-4：缺少 `AuditorAware` Bean

**问题**：`ArchiveApplication.java` 声明了 `@EnableJpaAuditing`，`BaseEntity` 使用了 `@CreatedBy` / `@LastModifiedBy`，但未提供 `AuditorAware<String>` 实现。

**涉及文件**：
- 新增 `com.archive.config.AuditorAwareImpl` 或直接在 `ArchiveApplication.java` 中添加 `@Bean`

**要求**：
1. 实现一个 `AuditorAware<String>`，从 `SecurityContextHolder` 获取当前用户名
2. 未认证时返回 `"system"`

```java
@Bean
public AuditorAware<String> auditorAware() {
    return () -> {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Optional.of("system");
        return Optional.of(auth.getName());
    };
}
```

**验收标准**：创建项目后，`created_by` 字段自动填充为当前登录用户名。

---

### P0-5：侧边栏图标未导入

**问题**：`Layout.vue` 使用了 `@element-plus/icons-vue` 的图标组件（DataLine/Folder/Document/AlarmClock/SetUp），但未导入。

**涉及文件**：
- `frontend/src/views/Layout.vue`

**要求**：
1. 在 `<script setup>` 中添加图标导入：
```typescript
import { DataLine, Folder, Document, AlarmClock, SetUp } from '@element-plus/icons-vue'
```
2. 删除重复的 `/knowledge` 菜单项（只保留启用的那个）

**验收标准**：侧边栏图标正常渲染，无重复菜单项。

---

### P0-6：Q&A 页面回车发送

**问题**：`Knowledge.vue` 使用 `<el-input type="textarea">` 输入问题，用户必须点击「提问」按钮才能发送，无法直接按回车提交。

**涉及文件**：
- `frontend/src/views/Knowledge.vue`

**要求**：
1. 将 `<el-input type="textarea">` 改为 `<el-input type="text">` 或保留 textarea 但增加键盘事件
2. 按 `Enter` 键时调用 `onAsk()` 提交问题
3. 按 `Shift+Enter` 换行（若使用 textarea 模式）
4. 发送按钮保留，作为备用操作

**实现方案（推荐）**：
```vue
<el-input
  v-model="question"
  type="textarea"
  :rows="3"
  placeholder="输入你的问题,回车发送, Shift+Enter 换行"
  maxlength="500"
  show-word-limit
  @keydown.enter.prevent="!$event.shiftKey && onAsk()"
/>
```

**验收标准**：在问答输入框按回车 → 直接发送问题并显示答案，Shift+Enter 正常换行。

---

## Phase 1 — 架构缺陷修复

### P1-1：`NoSuchElementException` 映射到 HTTP 404

**问题**：所有 Service 中 `findById().orElseThrow(() -> new NoSuchElementException())` 未被 `GlobalExceptionHandler` 捕获，落到 HTTP 500。

**涉及文件**：
- `backend/src/main/java/com/archive/common/GlobalExceptionHandler.java`

**要求**：
1. 新增 `NoSuchElementException` 处理器，返回 HTTP 404 + 错误码 `40400`
2. 对该异常不记录 error 级别的日志（避免告警噪音）

```java
@ExceptionHandler(NoSuchElementException.class)
public ResponseEntity<ApiResponse<Void>> handleNotFound(NoSuchElementException e, HttpServletRequest req) {
    log.info("资源不存在 @ {}: {}", req.getRequestURI(), e.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(40400, e.getMessage()));
}
```

**验收标准**：请求不存在的资源（如 `GET /api/projects/99999`）返回 HTTP 404，而非 500。

---

### P1-2：`NoSuchElementException` → 修改 Service 层异常类型（可选）

**问题**：`IllegalArgumentException` 已映射到 400，`NoSuchElementException` 映射到 404，但 Service 层有一部分业务用 `NoSuchElementException` 表达"找不到"，另一部分用 `IllegalStateException` 表达"状态不允许"。

**要求**：
- 确保所有"资源不存在"场景统一抛出 `NoSuchElementException`
- `IllegalStateException` 也应被 `GlobalExceptionHandler` 显式处理，返回 HTTP 400

---

### P1-3：Caddyfile 增加 HTTP 限流

**问题**：Caddyfile 中 `rate_limit` 只配置在 HTTPS 块，HTTP 块（端口 80）无限制。

**涉及文件**：
- `deploy/caddy/Caddyfile`

**要求**：
- 在 `:80` 块中增加同样的 `rate_limit @bottlenot 600r/m`

---

### P1-4：`config.example.json` 补充 `jwt.secret` 字段

**问题**：配置模板缺少 `jwt.secret`，用户不知道要配置 JWT 密钥。

**涉及文件**：
- `config/config.example.json`

**要求**：
- 在 JSON 根级别增加 `"jwt"` 段，包含 `secret` 和 `expirationSeconds` 字段
- 填写注释说明生成方法（`openssl rand -base64 32`）

---

## Phase 2 — 功能补齐

### P2-1：`chapter_summary` 表 + 实体 + 入库

**架构文档位置**：[`architecture/history/architecture-v3-final.md`](../architecture/history/architecture-v3-final.md) §3.2

**当前状态**：`SectionService.split()` 能切分章节，结果只通过 API 返回前端展示，未入库。架构文档中的 `chapter_summary` 表完全不存在。

**要求**：

**2-1.1 新增实体 `ChapterSummary`**
- 包: `com.archive.entity`
- 继承 `BaseEntity`
- 字段与架构文档一致：`id, materialVersionId, chapterNo, chapterTitle, content(MEDIUMTEXT), summary(TEXT), keywords(VARCHAR(512)), pageStart, pageEnd`

**2-1.2 新增 Repository `ChapterSummaryRepository`**
- `findByMaterialVersionIdOrderByChapterNo(Long materialVersionId)`
- `deleteByMaterialVersionId(Long materialVersionId)`
- `search(String keyword, Pageable pageable)` — 使用 `@Query` 调用 FULLTEXT MATCH AGAINST

**2-1.3 新增 Service `ChapterService`**
- `splitAndSave(Long materialVersionId)` — 获取解析文本 → `SectionService.split()` → 逐个保存章节
- 保存后更新 `MaterialVersion` 的解析状态
- 在 `parseVersion()` 成功后自动调用

**2-1.4 修改 `init.sql`**
- 新增 `chapter_summary` 表定义，包含 FULLTEXT 索引

**验收标准**：上传文件并解析后，`chapter_summary` 表中自动生成章节记录。

---

### P2-2：异步文档解析

**问题**：大文件（50MB+）解析时阻塞 HTTP 请求线程。

**涉及文件**：
- `MaterialVersionService.java`
- `ArchiveApplication.java`（可能需要加 `@EnableAsync`）

**要求**：
1. 在 `ArchiveApplication.java` 添加 `@EnableAsync`
2. 创建 `AsyncConfig` 配置线程池（core=2, max=4, queue=10）
3. 将 `parseVersion()` 中的 Tika 解析移到 `@Async` 方法中
4. 上传接口立即返回 `parseStatus=pending`，解析完成后更新状态
5. 前端 `ProposalDetail.vue` 中上传后提示"解析中，请稍后查看"

**验收标准**：上传一个 50MB PDF，接口在 3 秒内返回（而非等到解析完成）。

---

### P2-3：N+1 查询优化

**问题**：`MaterialController.list()` 中循环调用 `materialService.countVersions()`。

**涉及文件**：
- `MaterialService.java`
- `MaterialRepository.java`

**要求**：
1. 在 `MaterialRepository` 中添加批量查询：
```java
@Query("SELECT m.id, COUNT(v) FROM Material m LEFT JOIN MaterialVersion v ON v.materialId = m.id WHERE m IN :materials GROUP BY m.id")
List<Object[]> countVersionsForMaterials(@Param("materials") List<Material> materials);
```
2. `MaterialService` 增加批量查询方法
3. `MaterialController` 中一次性查询版本数，组装到返回结果

---

### P2-4：`StorageService` 增加 `deleteParsedText()`

**问题**：删除版本时，解析文本文件未被清理。

**涉及文件**：
- `StorageService.java`
- `MaterialVersionService.java`

**要求**：
```java
public boolean deleteParsedText(String relativePath) {
    try {
        Path target = resolveUnderRoot(parsedRoot, relativePath);
        return Files.deleteIfExists(target);
    } catch (IOException e) {
        log.warn("Failed to delete parsed text: {}", relativePath, e);
        return false;
    }
}
```

并在 `MaterialVersionService.deleteVersion()` 中调用此方法。

---

### P2-5：材料批量上传 + 统一设置界面

**问题**：目前上传材料必须：① 逐个点击「新建材料」弹出表单 → ② 填写标题/类别/状态/说明等 → ③ 点击保存 → ④ 再点击「上传版本」选文件上传。每个文件都要重复这四步，操作成本高。用户期望：一次性选择多个文件上传，解析完成后统一设置元数据。

**涉及文件**：
- `frontend/src/views/ProposalDetail.vue` — 新增批量上传入口
- `frontend/src/api/archive.ts` — 新增批量上传 API 调用
- `backend/src/main/java/com/archive/controller/MaterialController.java` — 新增批量上传接口
- `backend/src/main/java/com/archive/service/MaterialService.java` — 批量创建 + 上传
- `backend/src/main/java/com/archive/service/MaterialVersionService.java` — 批量解析

**要求**：

**2-5.1 后端 — 新增批量上传接口**
```
POST /api/proposals/{proposalId}/materials/batch-upload
Content-Type: multipart/form-data
Body: files[] (multiple files), defaultCategory (可选), defaultTags (可选)
```
- 每个文件自动创建一个 Material + 一个 MaterialVersion（版本号 v1）
- 标题用文件名（不含扩展名）作为默认值，允许后续编辑
- category 使用 `defaultCategory`（不传则用"其他"）
- status 统一设为"草稿"
- 触发 Tika 解析（同单文件流程）
- 返回创建的 Material 列表（含 versionId）

**2-5.2 前端 — 新增批量上传 UI**

在 `ProposalDetail.vue` 的材料列表增加「批量上传」按钮（放在「+ 新建材料」旁边）：
- 点击后弹出文件选择器（`input[multiple]`）
- 选择多个文件后立即上传（显示上传进度条或 loading）
- 上传完成后，自动刷新材料列表
- 不弹设置弹窗（简化初始流程：上传后再编辑元数据）

**2-5.3 前端 — 材料行内快速编辑**

材料列表表格增加行内快速编辑能力（而非打开完整弹窗）：
- 双击材料标题可直接修改
- 类别/标签字段使用 `el-select` / `el-tag` 直接编辑
- 或者提供一个「批量设置」按钮，选中多个材料后统一修改类别/标签/状态

**验收标准**：
1. 在议案详情页点击「批量上传」→ 选择 5 个文件 → 一次性上传成功
2. 材料列表中出现 5 条新记录，各含一个版本
3. 无需先填表单再上传

---

### P2-6：议案摘要自动提取

**问题**：用户创建议案时 `summary` 字段可能为空，但每个项目有立项报告（项目创建时生成）、上会时有申请报告。如果议案附言（remark）和摘要（summary）未填，系统应自动从这些报告内容中提取信息填充。

**涉及文件**：
- `backend/src/main/java/com/archive/service/ProposalService.java` — 提交时自动填充
- `backend/src/main/java/com/archive/service/GLMService.java` 或 `AISummaryService.java` — 提取摘要
- `backend/src/main/java/com/archive/entity/Proposal.java` — 确认有 summary/remark 字段（已有）
- `frontend/src/views/ProposalDetail.vue` — 展示时标记「自动提取」

**要求**：

**2-6.1 自动提取触发时机**
- 当提案状态从"草稿"变更为"已提交"时触发
- 触发条件：`proposal.summary` 为 null 或空字符串

**2-6.2 信息来源优先级**
1. 优先检查该议案下的材料中是否有"立项报告"（material.category = '立项报告'）的解析内容
2. 其次检查"申请报告"（material.category = '申请报告'）的解析内容
3. 若两者都无，扫描该议案下的所有已解析材料，取最新版本的 `parsed_text`

**2-6.3 提取方式**
- 调用智谱 GLM-4-Flash API：`"请从以下材料内容中提取关键信息，生成 200-500 字的议案摘要，包括：项目背景、主要风险、审议要点"`（Prompt 可配置）
- 提取成功后写入 `proposal.summary`
- 并在 `proposal.remark` 附加标注 `[摘要由系统自动生成于 {{datetime}}]`
- 调用失败或 API 不可用时不阻塞状态流转，记录 warn 日志

**2-6.4 前端提示**
- 在 `ProposalDetail.vue` 的摘要展示区域，如果是由系统自动生成的，显示 `<el-tag size="small" type="info">自动生成</el-tag>` 标记
- 提供「重新提取」按钮（调用同一接口）

**验收标准**：
1. 创建一个议案，留空 `summary`，上传一份 DOCX 立项报告并解析成功
2. 将议案状态改为"已提交"
3. 刷新后看到 `summary` 自动填充了 200-500 字的摘录，带「自动生成」标记

---

## Phase 3 — 增强功能

### P3-1：登录限流

**要求**：
1. 在后端登录接口增加 IP 级别的限流（如每分钟最多 5 次尝试）
2. 连续 5 次失败后临时锁定账号 15 分钟
3. 使用 `@Aspect` 或过滤器实现，不引入额外依赖

**涉及文件**：
- 新增 `LoginRateLimiter` 组件
- 修改 `AuthService.login()` 或 `AuthController.login()`

---

### P3-2：RBAC 权限控制

**要求**：
1. 在 `SecurityConfig` 中为各 API 端点添加 `hasRole()` / `hasAuthority()` 限制
2. 权限配置：
   - `admin` — 所有操作
   - `committee` — 查看项目/议案/材料 + 知识库问答
   - `project_owner` — 管理自己项目 + 上传材料
   - `employee` — 查看项目 + 知识库问答

---

### P3-3：文件去重

**要求**：
1. 上传时检查 SHA-256 是否已存在于同一 material 下
2. 若存在且文件完全相同，提示用户而不是创建重复版本
3. 若存在但属于不同 material，允许上传并标记

**涉及文件**：
- `MaterialVersionService.upload()`

---

### P3-4：SearchResult 片段提取改进

**问题**：当前 `extractSnippet()` 只匹配问题的第一个字符，对高频中文字符产生大量假阳性匹配。

**要求**：
- 使用 MySQL 的 `LOCATE()` 查找问题中的多字符关键词
- 优先选择关键词最密集的片段作为 snippet
- 备选：使用 BM25 算法评分

---

### P3-5：Logout 后清除浏览器历史

**要求**：
- 在 `Layout.vue` 的 `onLogout()` 中，退出后使用 `router.replace('/login')` 替代 `router.push('/login')`
- 或使用 `window.location.replace('/login')` 清除历史栈

---

### P3-6：参数配置表 — 动态管理项目/材料/议案分类与状态

**问题**：目前 `projectStatusOptions`、`projectCategoryOptions`、`proposalTypeOptions`、`materialCategoryOptions`、`materialStatusOptions` 等全部硬编码在 `frontend/src/api/archive.ts` 中，后端实体 `Project.category`、`Material.category` 等字段也使用字符串类型写死枚举值。管理员无法自定义分类，现有分类（如"股权类/固收类/混合类"）也不够准确。

**涉及文件**：

| 文件 | 操作 |
|------|------|
| `backend/.../entity/DictItem.java` | **新增** — 字典项实体 |
| `backend/.../entity/DictType.java` | **新增** — 字典分类实体（可选，可用 type_code 字符区分） |
| `backend/.../repository/DictItemRepository.java` | **新增** |
| `backend/.../service/DictService.java` | **新增** |
| `backend/.../controller/DictController.java` | **新增** — CRUD + 按 typeCode 查询 |
| `backend/.../resources/db/init.sql` | **修改** — 新增 `dict_type` + `dict_item` 表 + 种子数据 |
| `frontend/src/api/archive.ts` | **修改** — 删除硬编码数组，改为 API 调用 |
| `frontend/src/views/AdminDict.vue` | **新增** — 参数管理页面 |
| `frontend/src/router/index.ts` | **修改** — 新增 /admin/dict 路由 |
| `frontend/src/views/Layout.vue` | **修改** — 侧边栏增加"参数管理"菜单（仅 admin 可见） |

**要求**：

**3-6.1 数据库设计**

```sql
-- 字典分类表
CREATE TABLE dict_type (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type_code VARCHAR(64) NOT NULL UNIQUE,   -- 如 'project_category', 'project_status', 'material_category', 'proposal_type'
    type_name VARCHAR(128) NOT NULL,          -- 如 '项目类别', '项目状态', '材料类别', '议案类型'
    description VARCHAR(500),
    sort_order INT DEFAULT 0,
    is_system BOOLEAN DEFAULT FALSE,          -- 系统内置不允许删除
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 字典项表
CREATE TABLE dict_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type_code VARCHAR(64) NOT NULL,           -- 关联 dict_type.type_code
    item_key VARCHAR(64) NOT NULL,            -- 枚举值，如 '股权类', '草稿'
    item_value VARCHAR(256) NOT NULL,         -- 展示值（通常与 item_key 相同，可不同）
    sort_order INT DEFAULT 0,                 -- 排序
    is_default BOOLEAN DEFAULT FALSE,         -- 是否为默认值（新建时自动选中）
    enabled BOOLEAN DEFAULT TRUE,             -- 是否启用（禁用后不在下拉框出现）
    remark VARCHAR(500),
    UNIQUE KEY uk_type_item (type_code, item_key),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**3-6.2 种子数据**

在 `init.sql` 中插入与当前硬编码一致的初始数据，确保升级后功能不受影响。

**3-6.3 后端接口**

```
GET  /api/admin/dict-types              → 获取所有字典分类
GET  /api/admin/dict-items?typeCode=xxx  → 获取某分类下的字典项（仅 enabled=true）
POST /api/admin/dict-items              → 新增字典项
PUT  /api/admin/dict-items/{id}         → 修改字典项
DELETE /api/admin/dict-items/{id}       → 删除字典项（is_system 项禁止删除）

GET  /api/dict/options?typeCode=xxx     → 公开接口，供前端下拉框调用（无需 admin 权限）
```

**3-6.4 前端改动**

1. 删除 `archive.ts` 中的全部 `projectStatusOptions`、`projectCategoryOptions`、`proposalStatusOptions`、`proposalTypeOptions`、`materialStatusOptions`、`materialCategoryOptions` 硬编码数组
2. 新增 `getDictOptions(typeCode: string): Promise<DictOption[]>` API 方法
3. 所有用到这些选项的视图（`ProjectForm.vue`、`ProjectList.vue`、`ProjectDetail.vue`、`ProposalDetail.vue`）改为 `onMounted` 时调用 `getDictOptions()` 动态加载
4. 可以封装一个 composable `useDict(typeCode)` 统一管理加载和缓存

**3-6.5 参数管理页面 AdminDict.vue**

设计一个简单的参数管理界面：
- 左侧树/列表展示字典分类（dict_type）
- 右侧展示该分类下的字典项列表（表格）
- 支持新增/编辑/删除字典项
- 支持拖拽排序（可选）
- 仅 admin 角色可访问

**3-6.6 后端实体字段一致性**

修改 `Project`、`Proposal`、`Material` 等实体的枚举字段注释，说明其值来源于 `dict_item` 表，不强制外键约束（保持字符串字段灵活性）。

**验收标准**：
1. admin 登录后，在侧边栏看到「参数管理」菜单，点击进入
2. 可以看到"项目类别""项目状态""材料类别""议案类型"等分类
3. 新增一个字典项（如"项目类别"下增加"不动产类"），保存后刷新
4. 创建项目页面，类别下拉框出现"不动产类"
5. 旧数据不受影响（字符串字段不设外键约束）

---

## Phase 4 — 测试

### P4-1：后端集成测试

**要求**：
1. 为 `AuthService` 编写 `@SpringBootTest` 测试
2. 为 `ProjectService` 编写测试（含状态验证）
3. 为 `MaterialVersionService.upload()` 编写测试（含 SHA-256 验证）

**测试框架**：JUnit 5 + Mockito（已在 pom.xml 中）

**验收标准**：`mvn test` 全部通过。

---

### P4-2：前端 TypeScript 类型安全

**要求**：
1. 消除所有 `as any` 类型断言
2. 为 API 响应接口补全泛型参数
3. 启用 `noUnusedLocals: true` 和 `noUnusedParameters: true`

---

## 优先级与建议执行顺序

```
Phase 0.1 → P0-1, P0-2, P0-3         ← 必须先修，不然功能不工作
        ↓
Phase 0.2 → P0-4, P0-5               ← 基础缺陷
        ↓
Phase 0.3 → P0-6                     ← 小 UX 改进，可穿插
        ↓
Phase 1   → P1-1 ~ P1-4              ← 架构缺陷
        ↓
Phase 2   → P2-1 ~ P2-4              ← 补齐缺失功能
        ↓
Phase 2.2 → P2-5, P2-6               ← 批量上传 + 智能摘要（功能增强）
        ↓
Phase 3   → P3-1 ~ P3-5              ← 增强
        ↓
Phase 3.6 → P3-6                     ← 参数配置表（需要前端路由/权限基础）
        ↓
Phase 4   → P4-1, P4-2               ← 测试与治理
```

> 注：P0-6（回车发送）改动量极小，可在任意 Phase 间穿插执行。
> P2-5 依赖 Phase 0/P1 的文件上传和解析链路修复完毕后再实施。
> P3-6 依赖 Phase 1 的 RBAC 基础（P3-2）支撑 admin 菜单权限，建议在 P3-2 之后实施。

---

## 附录：与原有需求的冲突说明

本补充需求与 [`architecture/history/architecture-v3-final.md`](../architecture/history/architecture-v3-final.md) 保持一致，不改变原有架构决策：
- ✅ 不向量化，MySQL FULLTEXT 仍是主力检索
- ✅ 智谱 GLM-4-Flash + GLM-4V
- ✅ 仅脱敏证件号 + 人名
- ✅ MySQL 8.0.16 ngram 分词
- ✅ Spring Boot 3.3 + Vue 3 + Caddy + WinSW 单体部署

仅对代码实现层面的缺陷进行修补和补齐。

---

*本文档由 Sisyphus 基于代码审计结果（`REVIEW-REPORT.md`）生成，供开发 Agent 按计划执行。*

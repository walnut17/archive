# 投委会档案管理系统 — 代码审计报告

> 审计日期: 2026-06-08
> 审计范围: 全部后端(Java 17 + Spring Boot 3.3) + 前端(Vue 3 + TypeScript + Element Plus)
> 审计方式: 静态代码审查（未运行）
> 审计目标: 发现架构缺陷、代码错误、安全隐患，提出改进建议

---

## 目录

1. [架构问题与建议](#1-架构问题与建议)
2. [代码错误与修复建议](#2-代码错误与修复建议)
3. [安全缺陷](#3-安全缺陷)
4. [缺失功能建议](#4-缺失功能建议)
5. [代码质量/风格问题](#5-代码质量风格问题)
6. [总结优先级](#6-总结优先级)

---

## 1. 架构问题与建议

### 1.1 FULLTEXT 查询缺少全文索引 ❌ 严重

**问题**：`KnowledgeSearchService.search()`（`KnowledgeSearchService.java:85-93`）对 `material_version.parsed_text` 列执行 `MATCH(v.parsed_text) AGAINST(... IN BOOLEAN MODE)`，但 `init.sql` 并未在该列上创建 `FULLTEXT INDEX`。InnoDB 要求 FULLTEXT 索引存在于被搜索列上，否则查询会抛出 `Can't find FULLTEXT index matching the column list` 错误。

架构文档中的 FULLTEXT 索引仅建在 `chapter_summary` 表上，但该表在 `init.sql` 中根本没有创建，对应的 `ChapterSummary` 实体和 Repository 也完全不存在。

**建议**：
1. 在 `material_version.parsed_text` 上添加 FULLTEXT 索引（带 ngram parser）：
   ```sql
   ALTER TABLE material_version ADD FULLTEXT INDEX ft_parsed_text (parsed_text) WITH PARSER ngram;
   ```
2. 或按架构文档补建 `chapter_summary` 表，改为对 `chapter_summary.content` + `summary` 做 FULLTEXT 检索（更符合设计）。

---

### 1.2 前端分页页码 0-based vs 1-based 偏移 ❌ 严重

**问题**：后端 Spring Data `PageRequest.of(page, size)` 使用 **0-based** 页码。前端 Element Plus `<el-pagination>` 的 `v-model:current-page` 默认使用 **1-based** 页码。

流程：
- 初始 `query.page = 0`（第 1 页）→ 正确
- 用户点击第 2 页 → Element Plus 设 `query.page = 2` → 后端收到 `page=2`（实际是第 **3** 页）

**建议**：在前端查询时将 `query.page - 1` 传递给后端，或在 `@current-change` 事件中处理：
```typescript
// ProjectList.vue 中的 fetch 方法
const page = await listProjects({ page: query.value.page - 1, ... })
```

所有涉及分页的组件（ProjectList、ProposalList 等）都需要此修正。

---

### 1.3 `@EnableJpaAuditing` 缺少 `AuditorAware` Bean ⚠️ 中等

**问题**：`ArchiveApplication.java` 声明了 `@EnableJpaAuditing`，`BaseEntity` 中使用了 `@CreatedBy` / `@LastModifiedBy`，但项目中**没有提供 `AuditorAware<String>` 实现 Bean**。这会导致：
- Spring 启动时可能报错（取决于版本）
- `createdBy` / `updatedBy` 字段永远为 `null`

**建议**：添加 `AuditorAware` 实现，从 `SecurityContextHolder` 中获取当前用户名：
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

---

### 1.4 `ConfigJsonLoader` 路径查找顺序与文档矛盾 ⚠️ 中等

**问题**：代码（`ConfigJsonLoader.java:82-106`）的查找顺序是：①环境变量 → ②`./config/config.json` → ③`../config/config.json`。但 `ConfigJsonLoader.java` 的 Javadoc 和 `config/README.md` 都写的是先找 `./config/config.json` 再找环境变量。

环境变量本应作为最高优先级**覆盖**，但代码将其放在第一位意味着找不到环境变量路径后才会降级到默认路径。如果用户设置了无效的 `CONFIG_JSON_PATH` 环境变量，会打印警告然后继续查默认路径，行为尚可接受，但文档不一致会误导。

**建议**：统一文档/注释与代码逻辑，或改为：始终加载默认路径 + 环境变量覆盖。

---

### 1.5 配置模板缺少 `jwt.secret` 字段 ⚠️ 中等

**问题**：`config.example.json` 中**没有**包含 `jwt.secret` 配置项。虽然 `application.yml` 有默认值 `change-me-please-...`，但生产部署应当从外部配置文件注入。用户按模板配完后不会知道还需要添加 JWT 密钥。

**建议**：在 `config.example.json` 中添加：
```json
"jwt": {
    "_comment": "JWT 签名密钥 — 至少 32 字节,用 openssl rand -base64 32 生成",
    "secret": "请替换为 32 字节以上的随机字符串"
}
```

---

### 1.6 `NoSuchElementException` 未映射到 404 ⚠️ 中等

**问题**：`GlobalExceptionHandler.java` 没有处理 `java.util.NoSuchElementException`。所有 Service 中的 `.orElseThrow(() -> new NoSuchElementException(...))` 会直接落到 `Exception` 处理器，返回 HTTP 500。而语义上正确的响应是 HTTP 404。

**建议**：添加异常处理：
```java
@ExceptionHandler(NoSuchElementException.class)
public ResponseEntity<ApiResponse<Void>> handleNotFound(NoSuchElementException e, HttpServletRequest req) {
    log.warn("资源不存在 @ {}: {}", req.getRequestURI(), e.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.fail(40400, e.getMessage()));
}
```

---

### 1.7 大文件解析阻塞 HTTP 请求线程 ⚠️ 中等

**问题**：`MaterialVersionService.upload()`（第 101 行）同步调用 `parseVersion()`，而 `parseVersion()` 使用 Tika 解析整个文件。对于 100MB 的文档，这可能耗时数十秒甚至更久，**完全阻塞 HTTP 响应线程**。

**建议**：
1. 将解析改为异步（`@Async` + 线程池），立即返回 `parseStatus=pending`
2. 前端轮询版本状态或使用 WebSocket 通知
3. 或至少将 `parseVersion` 放到 `@Transactional` 事件监听器里异步执行

---

### 1.8 知识库检索未转义 FULLTEXT BOOLEAN MODE 特殊字符 ⚠️ 中等

**问题**：`KnowledgeSearchService.java:75` 只转义了 `"`、`+`、`-`，但 MySQL 的 BOOLEAN MODE 还有很多操作符：`*`、`>`、`<`、`(`、`)`、`~`、`@`。用户输入含这些字符可能导致检索行为异常或语法错误。

**建议**：用更完善的转义，或在搜索前用正则移除所有 BOOLEAN MODE 特殊字符：
```java
String sanitized = question.replaceAll("[+\-<>()~*@\"]", " ");
```

---

## 2. 代码错误与修复建议

### 2.1 前端多处 `resp.data` 重复解包 ❌ 严重

**根源**：`api/archive.ts` 中的 `getData<T>(response)` 已从 Axios 响应中提取 `.data.data`，返回直接是业务数据对象。但前端视图组件仍对返回结果重复访问 `.data`。

**涉及文件和行**：

| 文件 | 行号 | 错误代码 | 正确写法 |
|------|------|----------|---------|
| `ProjectForm.vue` | 31-32 | `const resp = await getProject(id); form.value = resp.data` | `form.value = await getProject(id)` |
| `ProjectDetail.vue` | 37-38 | `const [p, pl] = await ...; project.value = (p as any).data; proposals.value = ((pl as any).data.content)` | `const p = await getProject(...); proposals.value = (await listProposals(...)).content` |
| `ProposalDetail.vue` | 44-45 | `proposal.value = (p as any).data; materials.value = ((ms as any).data.content)` | 同上 |
| `ProposalDetail.vue` | 93-94 | `const resp = await listVersions(m.id!); versions.value = resp.data` | `versions.value = await listVersions(m.id!)` |
| `ProposalDetail.vue` | 106 | `const resp = await uploadVersion(...); ElMessage.success(...resp.parseStatus)` | `const v = await uploadVersion(...); ElMessage.success(...v.parseStatus)` |
| `ProposalDetail.vue` | 139-140 | `const resp = await listSections(...); sections.value = resp.data` | `sections.value = await listSections(...)` |

**影响**：编辑表单/详情页无法显示数据，上传版本后提示信息为 `undefined`。

---

### 2.2 前端 `Login.vue` 的 `formRef` 未使用

**问题**：`Login.vue:17` 声明了 `const formRef = ref()` 并在模板 `<el-form ref="formRef">` 中使用，但 `formRef.value` 从未被使用（既不做 validate 也不做 resetFields），属于**无用变量**。

**建议**：移除 `formRef` 声明和模板中的 `ref` 引用。

---

### 2.3 前端 `Layout.vue` 中 `/knowledge` 菜单项重复

**问题**：`Layout.vue:66-68` 和 `Layout.vue:72-75` 有**两个**指向 `/knowledge` 的菜单项——一个启用，一个禁用。这会导致菜单显示异常，两个项目可能同时高亮。

**建议**：删除第 72-75 行的重复菜单项，只保留启用的那个。

---

### 2.4 前端多处 `catch {}` 空吞异常

**问题**：以下位置使用了空的 `catch` 块，导致异常被静默吞掉，不利于调试：

| 文件 | 行号 |
|------|------|
| `ProposalDetail.vue` | 78, 110, 137 |
| `Dashboard.vue` | 22-24 |

**建议**：至少在开发阶段 `console.error(e)`，或使用统一错误处理。

---

### 2.5 `MaterialVersionService.deleteVersion()` 解析文本文件未删除

**问题**：`MaterialVersionService.java:176-179` 在删除版本时注释提示"解析文本路径不在 file-root 下，这里简化处理"，但**实际没有删除 parsed-root 下的解析文本文件**，导致"幽灵文件"积累。

**建议**：在 `StorageService` 中添加 `deleteParsedText()` 方法并在删除版本时调用：
```java
if (v.getParsedTextPath() != null) {
    storageService.deleteParsedText(v.getParsedTextPath());
}
```

---

### 2.6 `SectionService` 的 "numeric" 正则可能误匹

**问题**：`SectionService.java:40` 的正则 `(?m)^\\s*\\d+(\\.\\d+){0,3}[\\s\\.、:：]` 会匹配行首的数字序列，包括：
- 日期如 `2026.` 
- 年份 `2026 `
- 金额数字 `1000 `

这些并非章节标题，会产生大量假阳性切分。

**建议**：增加限制条件，如 `\\d+[.、]` 要求数字后紧跟分隔符，且长度 ≤ 4；或将序号识别限制在三位以内：
```java
Pattern.compile("(?m)^\\s*(?:[1-9]\\d?|[1-9]\\d?[.、]\\d+)[\\s\\.、:：]")
```

---

### 2.7 `TikaService` 使用默认 `Tika()` 实例

**问题**：`TikaService.java:25` 使用 `new Tika()` 默认配置，会注册所有可用的解析器。这在生产环境可能带来：
1. 解析器过多导致内存占用高
2. 某些格式的解析器可能在 2.9.x 版本中存在安全漏洞（如 XXE）

**建议**：使用 `TikaConfig` 显式指定需要的解析器类型，限制到 docx、xlsx、pdf、txt 等业务所需格式。

---

## 3. 安全缺陷

### 3.1 登录接口无防暴力破解机制 ❌ 高

**问题**：`/api/auth/login` 没有验证码、没有 IP 级别限流、没有账户锁定机制。攻击者可以无限尝试密码。

**建议**：
1. 添加登录失败次数计数，连续失败 N 次后临时锁定账号
2. 引入简单的 IP 级别限流（可用 Spring 过滤器或 Bucket4j）
3. 或部署在 Caddy 层做限流

---

### 3.2 `CorsConfigurationSource` 在生产环境过于宽松 ⚠️ 中

**问题**：`SecurityConfig.java:69-73` 的 CORS 配置允许 `https://*` 的所有域名。在有反代的内部部署场景下，这可能允许来自内部其他站点的跨域请求。

**建议**：生产环境中 CORS 应限定到具体的 Caddy 域名，或通过配置注入允许的源头列表。

---

### 3.3 `application.yml` 硬编码 MySQL 密码占位符默认值

**问题**：`application.yml:11` 中 `password: ${app.database.password:}` 空字符串作为默认密码。如果 `config.json` 加载失败，应用会以**空密码**连接数据库。

**建议**：不在配置中存在密码时直接启动失败，或者在应用启动时验证关键配置不为空。

---

## 4. 缺失功能建议

### 4.1 审计日志（Audit Log）— M5 功能但应提前规划

**当前状态**：完全没有审计日志实现。所有实体的变更（谁在什么时候删了什么项目、谁上传了什么文件）无法回溯。

**建议**：
- 使用 Spring Data Envers（Hibernate 的审计扩展）自动记录实体变更
- 或使用 AOP 拦截 Controller/Service 方法记录操作日志
- 重点审计：删除操作、权限变更、文件上传/删除

---

### 4.2 文件去重功能不完整

**当前状态**：`MaterialVersion` 存储了 `sha256` 字段，也有 `findBySha256` 查询，但上传逻辑（`MaterialVersionService.upload()`）并未使用去重——相同的文件每次上传都会创建新版本。

**建议**：在上传前检查 `sha256` 是否已存在于同 material 下，若存在则直接复用版本号或提示用户。

---

### 4.3 `chapter_summary` 表缺失

**当前状态**：架构文档 v3 详细设计了 `chapter_summary` 表作为知识库核心，但：
1. `init.sql` 没有创建该表
2. 没有对应的 JPA 实体  
3. 没有对应的 Repository  
4. 章节切分结果 (`SectionService.split()`) 当前仅通过 `/sections` API 返回给前端展示，**未入库**

**建议**：按架构文档补全 `chapter_summary` 表、实体、Repository，并在 `parseVersion()` 中串联章节切分 + 入库流程。

---

### 4.4 PDF 页码映射缺失

**当前状态**：`TikaService.extractText()` 只提取纯文本，丢失了页码信息。而架构文档要求"章节定位"支持页码引用，`Section` 类的 `pageStart` / `pageEnd` 也从未被填充。

**建议**：使用 Tika 的 `WriteOutContentHandler` + `ToXMLContentHandler` 获取包含页码的结构化内容，或使用 PDFBox 直接提取带页码的文本。

---

### 4.5 批量操作能力缺失

**当前状态**：项目/议案/材料仅支持单条 CRUD，没有批量删除、批量状态切换等功能。

**建议**：后续可以添加批量操作端点（`DELETE /api/projects?ids=1,2,3` 等）。

---

## 5. 代码质量/风格问题

### 5.1 后端 DTO 类 `@Data` 与不可变字段混用

`MeInfo`（`AuthController.java:59-70`）和 `HealthInfo`（`HealthController.java:26-33`）使用 final 字段 + 显式构造器，但同时标记了 `@Data`（含 `@Setter`）。final 字段配合 `@Setter` 虽然不会产生 setter，但语义不清晰。

**建议**：这些不可变 DTO 应使用 `@Value` 或 `public record`（Java 17 原生）。

---

### 5.2 前端的 `@ts-ignore` / `as any` 泛滥

前端大量使用 `as any` 类型断言（如 `(p as any).data`），直接绕过了 TypeScript 的类型检查。在修复了 2.1 的数据流问题后，应能消除大部分 `as any`。

---

### 5.3 后端代码风格不一致

- `BaseEntity` 使用 `LocalDateTime`，但 `User` / `Role` 自己又声明了 `createdAt` / `updatedAt` 字段，不使用 `BaseEntity` 继承
- `Project`、`Proposal`、`Material`、`MaterialVersion` 使用 `BaseEntity`，但 `User` / `Role` 没有

**建议**：统一让所有实体继承 `BaseEntity`。

---

## 6. 总结优先级

| 优先级 | 问题 | 影响 | 修复难度 |
|--------|------|------|---------|
| 🔴 P0 | 2.1 前端 `resp.data` 重复解包 | 编辑/详情/上传功能全部不可用 | 低 |
| 🔴 P0 | 1.1 FULLTEXT 查询无索引 | 知识库检索直接报错 | 低 |
| 🔴 P0 | 1.2 分页偏移 bug | 第 2 页开始数据不对 | 低 |
| 🟠 P1 | 1.3 缺少 AuditorAware | 审计字段全为 null | 低 |
| 🟠 P1 | 1.6 NoSuchElement → 500 | 资源不存在时返回 500 | 低 |
| 🟠 P1 | 1.7 同步解析阻塞 | 大文件上传可能前端超时 | 中 |
| 🟠 P1 | 1.4 配置路径文档不一致 | 维护者困惑 | 低 |
| 🟠 P1 | 2.3 菜单项重复 | 侧边栏显示异常 | 低 |
| 🟡 P2 | 3.1 登录无限流 | 暴力破解风险 | 中 |
| 🟡 P2 | 4.3 chapter_summary 缺失 | 知识库核心功能不完整 | 高 |
| 🟡 P2 | 1.8 FULLTEXT 字符转义不足 | 特殊字符导致检索异常 | 低 |
| 🟡 P2 | 2.5 解析文本未删除 | 幽灵文件积累 | 低 |
| 🟢 P3 | 5.x 代码风格问题 | 可维护性 | 低 |
| 🟢 P3 | 4.2 文件去重未实现 | 存储空间浪费 | 中 |
| 🟢 P3 | 2.7 Tika 默认配置 | 潜在安全、性能问题 | 中 |

---

## 附：审查方法

- 逐文件阅读全部 39 个后端 Java 源文件（含 entity/repository/service/controller/config/dto/common）
- 逐文件阅读全部 15 个前端 TypeScript/Vue 源文件（含 api/store/router/views）
- 检查 SQL DDL、配置、部署脚本
- 对比架构文档 v3 与实际代码的一致性
- 对比后端 API 合约与前端调用模式的一致性

*本报告仅基于静态代码审查，未执行编译或运行测试。*

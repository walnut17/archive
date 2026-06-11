# Archive 本地只读文件 Agent 工具 — 架构说明

> **Plan**：[`upgrade_to_settle/plan-2026-06-11-archive-local-fs-tools.md`](../../upgrade_to_settle/plan-2026-06-11-archive-local-fs-tools.md)（UP-0611-01）  
> **需求**：[`REQUIREMENTS.md`](../requirements/REQUIREMENTS.md) §5.6.7  
> **RI**：[`ARCH-DECOMPOSITION.md`](../requirements/ARCH-DECOMPOSITION.md) RI-70  
> **状态**：设计已定稿 · **实现未开始**

---

## 1. 背景与动机

当前 Agent 材料检索**仅依赖 MySQL**（`search_fulltext` → `material_version.parsed_text`）。磁盘上另有：

| 路径 | 内容 |
|---|---|
| `D:/archive/files/` | 上传原始件（PDF/Word/…） |
| `D:/archive/parsed/` | Tika 解析 `.txt` 副本 |

预览 / 下载 API 已通过 `StorageService` 读盘，但 **Agent 无工具**访问。联测与业务方要求：在**安全边界内**提供类似 **ls / grep / read** 的能力，并**预留多模态**扩展。

---

## 2. 设计原则

1. **只读**：无 write/delete/exec。
2. **双根白名单**：仅 `app.storage.file-root`、`app.storage.parsed-root`。
3. **DB 绑路径优先**：LLM 传 `materialVersionId`，后端查 `storage_path` / `parsed_text_path`，避免路径幻觉。
4. **与 MySQL 工具互补**：不替代 `search_fulltext`；大目录浏览、对照原件文件名时用 `archive_fs`。
5. **可扩展**：`ArchiveFsAction` 插件式注册；多模态另开 RI-71。

---

## 3. 组件结构

```text
com.archive.agent.tool.archive/
├── ArchiveFsTool.java              # AgentTool 门面, name=archive_fs
├── ArchivePathGuard.java           # zone + relative → Path
├── ArchiveMaterialPathResolver.java
├── ArchiveFsContext.java
├── ArchiveFsAction.java            # 扩展接口
├── ArchiveFsActionRegistry.java
├── ListArchiveAction.java          # action=list
├── GrepArchiveAction.java          # action=grep
└── ReadTextArchiveAction.java      # action=read
```

### 3.1 ArchivePathGuard

- 输入：`zone`（`files`|`parsed`）+ `relativePath`
- 映射 root：`file-root` / `parsed-root`
- 算法：与 [`StorageService.resolveUnderRoot`](../../backend/src/main/java/com/archive/common/StorageService.java) 相同 — `normalize` + `startsWith(root)`
- 失败：抛 `ArchivePathDeniedException` → ToolResult.error（不泄露内部路径）

### 3.2 ArchiveMaterialPathResolver

1. 读 `material_version` by id  
2. 校验 material → proposal → project 与 `AgentContext.lockedProjectCode` 一致（若已锁定）  
3. 返回 `storage_path` 或 `parsed_text_path`（相对路径）

### 3.3 Action 行为

| action | 行为 | 限制 |
|---|---|---|
| `list` | 列目录下一层（文件+子目录名） | max 100 entries |
| `grep` | 逐行匹配 `pattern`（substring，可选简单 regex） | max 200 lines；文件 max 2MB 读入 |
| `read` | 读文本前 N 字节 | max 512KB |

**files 区 grep/read**：仅允许扩展名白名单 `.txt` `.md` `.csv` `.json` `.xml` `.html`；其它提示「请用 zone=parsed 或 search_fulltext」。

---

## 4. Agent 集成

- 注册：Spring `@Component` 实现 `AgentTool`，由现有 `AgentEngine` `List<AgentTool>` 自动收集。
- Prompt：[`AgentSystemPrompt`](../../backend/src/main/java/com/archive/agent/prompt/AgentSystemPrompt.java) 增加第 8 工具说明。
- 推荐链路：`find_project` → `archive_fs`（grep/list）→ `FINAL_ANSWER`；统计类仍用 `query_mysql` / `get_project_business_data`。

---

## 5. 安全与审计

| 项 | 措施 |
|---|---|
| Path traversal | PathGuard |
| DoS | maxBytes / maxLines / 5s timeout |
| 越权项目 | MaterialPathResolver 校验 project |
| 审计 | audit_log：`ARCHIVE_FS_READ` + action + relativePath 摘要 |
| 配置 | `spring.ai.agent.archive-fs.max-read-bytes` 等 |

**明确禁止的根**：`log-root`、`D:/archive/config`、项目源码目录。

---

## 6. 多模态扩展（RI-71，本期不实现）

```java
@Component
public class ReadPdfPageAction implements ArchiveFsAction {
    @Override public String actionName() { return "read_pdf_page"; }
    // 未来：渲染 PDF 单页 → base64 / 外部 vision API
}
```

新 action 仅需：实现接口 + `@Component` + 单测 + prompt 文档更新；**不改** `ArchiveFsTool` 门面签名（门面根据 `action` 字段分发）。

---

## 7. 配置项（application.yml 草案）

```yaml
spring:
  ai:
    agent:
      archive-fs:
        enabled: true
        max-read-bytes: 524288
        max-grep-lines: 200
        max-list-entries: 100
        files-text-extensions: txt,md,csv,json,xml,html
        timeout-ms: 5000
```

---

## 8. 测试策略

- **单元**：PathGuard、`GrepArchiveAction` 用 JUnit `@TempDir`
- **集成**：Agent 场景 mock `ArchiveFsTool` 或 test profile 指向 temp root
- **联测**：125 + 真实 `D:/archive` 下已有材料

---

## 9. 与部署目录关系

[`05-deployment-and-environment.md`](05-deployment-and-environment.md) 中 `D:/archive/*` 布局不变；本工具**不新增**磁盘目录，只读现有 `files/`、`parsed/`。

---

*Sisyphus 架构风格 · 2026-06-11 · 随 UP-0611-01 实现同步更新 §3 实现细节*

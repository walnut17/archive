# MOD-04 — 业务功能新增（看板 / 通知 / 导出 / 预览 / 脱敏 / 导入）

> **接手 agent 只需读本文 + `MOD-01` SQL + `MOD-02` Service + `MOD-03` Agent 工具 即可开工**
> **本模块含前端 + 后端，全栈 agent**

---

## §0 模块目标

v1.1 6 大新业务功能：
- **RI-62 项目看板**：3 视图 + 7 筛选 + 4 排序 + 9 列
- **RI-63 通知中心**：4 类来源 + 已读/未读 + 30s 轮询
- **RI-64 数据导出**：PDF（OpenPDF）/ Excel（POI）4 类列表
- **RI-65 附件预览**：PDF/Word/图片/文本 浏览器内嵌预览
- **RI-66 关键事实变更对比**：JSON tree diff + 证据引用
- **RI-68 旧系统 Excel 导入**：4 类模板 + 字段校验 + 错误明细
- **RI-69 数据脱敏视图**：委员视图脱敏 + 申请查看留痕

---

## §1 涉及 RI

| RI | 后端 | 前端 |
|---|---|---|
| RI-62 项目看板 | T-v1.1-20 | T-v1.1-31 |
| RI-63 通知中心 | T-v1.1-21 | T-v1.1-30 |
| RI-64 数据导出 | T-v1.1-22 | (沿用 ProjectDetail.vue "导出"按钮) |
| RI-65 附件预览 | T-v1.1-23 | T-v1.1-32 |
| RI-66 事实变更对比 | T-v1.1-27 | T-v1.1-33 |
| RI-68 旧系统导入 | T-v1.1-25 | T-v1.1-34 |
| RI-69 脱敏视图 | T-v1.1-26 | T-v1.1-35 |

---

## §2 涉及文件（独占清单）

### 2.1 后端新建（8 个文件）

```
backend/src/main/java/com/archive/
├── entity/
│   ├── Notification.java                   (新, RI-63)
│   └── ImportBatch.java + ImportError.java (新, RI-68)
├── repository/
│   ├── NotificationRepository.java         (新, RI-63)
│   ├── ImportBatchRepository.java          (新, RI-68)
│   └── ImportErrorRepository.java          (新, RI-68)
├── service/
│   ├── ProjectBoardService.java            (新, RI-62)
│   ├── NotificationService.java            (新, RI-63)
│   ├── ExportService.java                  (新, RI-64, OpenPDF + POI)
│   ├── PreviewService.java                 (新, RI-65)
│   ├── ImportService.java                  (新, RI-68)
│   └── MaskingService.java                 (新, RI-69)
└── controller/
    ├── ProjectBoardController.java         (新, RI-62)
    ├── NotificationController.java         (新, RI-63)
    ├── ImportController.java               (新, RI-68)
    └── (ProjectController / MaterialController 改, 见 §2.2)
```

### 2.2 后端修改（5 个文件，独占）

```
backend/src/main/java/com/archive/
├── controller/
│   ├── ProjectController.java              (改, 加 export/rollback/unmask 端点)
│   ├── MaterialController.java             (改, 加 preview 端点)
│   └── QaController.java                   (不改, MOD-03 已改响应体)
├── service/
│   ├── ProjectFactEventService.java        (改, 加 getDiff 方法)
│   └── ProjectService.java                 (改, 加 mask 字段)
└── repository/
    └── ProjectRepository.java              (改, 加 findBoardView 聚合查询)
```

### 2.3 前端新建（8 个文件）

```
frontend/src/
├── components/
│   ├── NotifBell.vue                       (新, RI-63)
│   ├── DiffViewer.vue                      (新, RI-66, jsondiffpatch)
│   ├── PreviewFrame.vue                    (新, RI-65, pdfjs + mammoth)
│   └── MaskedField.vue                     (新, RI-69)
├── views/
│   ├── ProjectBoard.vue                    (新, RI-62)
│   ├── Notification.vue                    (新, RI-63)
│   ├── ImportWizard.vue                    (新, RI-68)
│   └── RecycleBin.vue                      (新, RI-55, MOD-02 已建 Service)
├── store/
│   └── notification.ts                     (新, RI-63, Pinia)
└── api/
    └── notification.ts                     (新, RI-63, axios 封装)
```

### 2.4 前端修改（5 个文件，独占）

```
frontend/src/
├── views/
│   ├── Layout.vue                          (改, RI-63 加 NotifBell)
│   ├── ProjectDetail.vue                   (改, RI-65/66/69 显示 + 弹窗)
│   ├── ProjectList.vue                     (改, RI-62 加看板入口)
│   └── ProjectForm.vue                     (改, RI-54 失败 banner, MOD-03 提供 failureType)
├── router/
│   └── index.ts                            (改, 增 4 路由)
├── api/
│   └── archive.ts                          (改, 增 30+ 端点)
└── package.json                            (改, 加 pdfjs-dist + mammoth + jsondiffpatch + dayjs + openpdf 后端 + poi 后端)
```

### 2.5 POM 修改（1 个）

```
backend/pom.xml                             (改, 加 OpenPDF 2.0.2 + Apache POI 5.2.5)
```

**总计**：8 新 + 5 改后端 + 8 新 + 5 改前端 + 1 pom = **27 个文件**

---

## §3 设计要点

### 3.1 RI-62 项目看板

```java
// controller/ProjectBoardController.java
@RestController
@RequestMapping("/api/projects/board")
public class ProjectBoardController {
    
    @GetMapping
    public ResponseEntity<BoardResponse> list(
        @RequestParam(defaultValue = "table") String view,        // table | card | kanban
        @RequestParam(required = false) String region,
        @RequestParam(required = false) String stage,
        @RequestParam(defaultValue = "updatedAt") String sort,
        @RequestParam(defaultValue = "desc") String order,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        BoardResponse resp = projectBoardService.list(view, region, stage, sort, order, page, size);
        return ResponseEntity.ok(resp);
    }
}

// service/ProjectBoardService.java
@Service
public class ProjectBoardService {
    
    public BoardResponse list(String view, String region, String stage, String sort, String order, int page, int size) {
        // 9 列: name, code, region, stage, amount, proposalCount, todoCount, lastUpdated, masked
        // 聚合查询: 累计议案数 / 待办数 / 最后更新时间
        List<ProjectBoardItem> items = projectRepo.findBoardView(region, stage, sort, order, page, size);
        
        // 看板视图按 stage 分组
        if ("kanban".equals(view)) {
            return BoardResponse.kanban(items.stream()
                .collect(Collectors.groupingBy(ProjectBoardItem::getStage)));
        }
        return BoardResponse.table(items, items.size());
    }
}

// repository/ProjectRepository.java (扩)
@Query("""
    SELECT new com.archive.dto.ProjectBoardItem(
        p.id, p.code, p.name, p.region, p.stage, p.amount,
        COUNT(DISTINCT pr.id),
        COUNT(DISTINCT t.id),
        p.updatedAt
    )
    FROM Project p
    LEFT JOIN Proposal pr ON pr.projectId = p.id
    LEFT JOIN Todo t ON t.projectId = p.id AND t.status = 'PENDING'
    WHERE (:region IS NULL OR p.region = :region)
      AND (:stage IS NULL OR p.stage = :stage)
    GROUP BY p.id, p.code, p.name, p.region, p.stage, p.amount, p.updatedAt
    ORDER BY 
      CASE WHEN :sort = 'amount' AND :order = 'desc' THEN p.amount END DESC,
      CASE WHEN :sort = 'updatedAt' AND :order = 'desc' THEN p.updatedAt END DESC
    """)
Page<ProjectBoardItem> findBoardView(
    @Param("region") String region,
    @Param("stage") String stage,
    @Param("sort") String sort,
    @Param("order") String order,
    Pageable pageable
);
```

### 3.2 RI-63 通知中心

```java
// entity/Notification.java
@Entity
@Table(name = "notification", indexes = @Index(name = "idx_user_read", columnList = "user_id, read, created_at"))
public class Notification {
    @Id @GeneratedValue private Long id;
    private Long userId;
    @Enumerated(EnumType.STRING) private NotificationType type;  // TODO / PROPOSAL / FACT / SYSTEM
    private String title;
    @Column(length = 1000) private String content;
    private String link;  // 跳转 URL
    private Boolean read = false;
    private LocalDateTime createdAt;
}

// controller/NotificationController.java
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    
    @GetMapping
    public ResponseEntity<Page<Notification>> list(
        @RequestParam(defaultValue = "false") Boolean unread,
        Pageable pageable
    ) {
        return ResponseEntity.ok(notificationService.list(unread, pageable));
    }
    
    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/mark-all-read")
    public ResponseEntity<?> markAllRead() {
        notificationService.markAllRead();
        return ResponseEntity.ok().build();
    }
}
```

**前端 30s 轮询**（不引 SSE）：
```typescript
// store/notification.ts
export const useNotificationStore = defineStore('notification', {
  state: () => ({
    notifications: [] as Notification[],
    unreadCount: 0,
    pollingTimer: null as number | null,
  }),
  actions: {
    startPolling() {
      this.pollingTimer = window.setInterval(() => this.fetchUnread(), 30_000);
    },
    stopPolling() {
      if (this.pollingTimer) {
        clearInterval(this.pollingTimer);
        this.pollingTimer = null;
      }
    },
    async fetchUnread() {
      const res = await notificationApi.listUnread();
      this.notifications = res.data.content;
      this.unreadCount = res.data.content.length;
    },
  },
});
```

### 3.3 RI-64 数据导出（D-4 OpenPDF + POI）

```java
// service/ExportService.java
@Service
public class ExportService {
    
    public byte[] exportProjectPdf(Long projectId) throws Exception {
        Project p = projectRepo.findById(projectId).orElseThrow();
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4);
        PdfWriter.getInstance(doc, baos);
        doc.open();
        
        doc.add(new Paragraph("项目报告 - " + p.getCode()));
        doc.add(new Paragraph("项目名称: " + p.getName()));
        doc.add(new Paragraph("项目金额: " + p.getAmount() + " 万元"));
        doc.add(new Paragraph("项目阶段: " + p.getStage()));
        // ... 详细报告内容
        
        doc.close();
        
        // 审计
        auditLogService.logExport(getCurrentUserId(), "project_pdf", projectId);
        
        return baos.toByteArray();
    }
    
    public byte[] exportProjectsExcel(String type) throws Exception {
        SXSSFWorkbook wb = new SXSSFWorkbook();
        Sheet sheet = wb.createSheet(type);
        
        // 表头
        Row header = sheet.createRow(0);
        switch (type) {
            case "materials" -> {/* 列: code, name, type, size, createdAt */ }
            case "proposals" -> {/* 列: code, projectCode, title, status, meetingResult */ }
            case "facts" -> {/* 列: projectCode, factType, factValue, confidence */ }
            case "projects" -> {/* 列: code, name, region, stage, amount */ }
        }
        
        // 数据行（从 DB 查）
        // ...
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        wb.write(baos);
        wb.close();
        
        // 审计
        auditLogService.logExport(getCurrentUserId(), type + "_xlsx", null);
        
        return baos.toByteArray();
    }
}

// controller/ProjectController.java (扩)
@GetMapping("/{id}/export")
public ResponseEntity<byte[]> exportProject(
    @PathVariable Long id,
    @RequestParam(defaultValue = "pdf") String format
) {
    byte[] bytes = "pdf".equals(format)
        ? exportService.exportProjectPdf(id)
        : exportService.exportProjectExcel("project-" + id);
    
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=project-" + id + "." + format)
        .body(bytes);
}
```

**POM 加依赖**（D-4）：
```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.github.librepdf</groupId>
    <artifactId>openpdf</artifactId>
    <version>2.0.2</version>
</dependency>
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

### 3.4 RI-65 附件预览（D-5 pdfjs + mammoth，纯前端）

```java
// controller/MaterialController.java (扩)
@GetMapping("/{id}/preview")
public ResponseEntity<byte[]> preview(@PathVariable Long id, @RequestParam(required = false) Integer version) {
    Material m = materialService.getForPreview(id, version);
    
    // PDF / 图片 / 文本: 直接返回流
    // Word: 前端用 mammoth 转 HTML (后端不引 LibreOffice)
    String mimeType = m.getMimeType();
    
    return ResponseEntity.ok()
        .header("Content-Type", mimeType)
        .header("Cache-Control", "private, max-age=3600")
        .body(m.getContent());
}
```

**前端**：
```vue
<!-- components/PreviewFrame.vue -->
<template>
  <div class="preview-frame">
    <iframe v-if="isPdf" :src="pdfUrl" width="100%" height="600px" />
    <div v-else-if="isWord" ref="wordContainer"></div>
    <img v-else-if="isImage" :src="imageUrl" />
    <pre v-else>{{ textContent }}</pre>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue';
import * as pdfjsLib from 'pdfjs-dist';
import mammoth from 'mammoth';

const props = defineProps<{ materialId: number; version?: number }');

const isPdf = computed(() => props.mimeType?.includes('pdf'));
const isWord = computed(() => props.mimeType?.includes('word') || props.mimeType?.includes('document'));
const isImage = computed(() => props.mimeType?.startsWith('image/'));

onMounted(async () => {
  if (isWord.value) {
    const res = await fetch(`/api/materials/${props.materialId}/preview`);
    const arrayBuffer = await res.arrayBuffer();
    const result = await mammoth.convertToHtml({ arrayBuffer });
    wordContainer.value.innerHTML = result.value;
  }
});
</script>
```

**package.json 加依赖**（D-5）：
```json
{
  "dependencies": {
    "pdfjs-dist": "^4.0",
    "mammoth": "^1.7",
    "jsondiffpatch": "^0.5",
    "dayjs": "^1.11"
  }
}
```

### 3.5 RI-66 关键事实变更对比

```java
// service/ProjectFactEventService.java (扩 getDiff)
public FactEventDiff getDiff(Long eventId) {
    ProjectFactEvent evt = repo.findById(eventId).orElseThrow();
    
    // 找上一条同 factKey 的 event (作为 before)
    ProjectFactEvent before = repo.findPreviousByFactKey(
        evt.getProjectId(), evt.getFactKey(), evt.getCreatedAt()
    );
    
    return new FactEventDiff(
        before != null ? before.getFactValue() : null,
        evt.getFactValue(),
        evt.getEvidence()
    );
}

// controller/ProjectFactEventController.java (扩)
@GetMapping("/{eventId}/diff")
public ResponseEntity<FactEventDiff> getDiff(@PathVariable Long eventId) {
    return ResponseEntity.ok(factEventService.getDiff(eventId));
}
```

**前端 JSON tree diff**：
```vue
<!-- components/DiffViewer.vue -->
<template>
  <el-dialog v-model="visible" title="事实变更对比" width="800px">
    <div v-if="diff">
      <h4>变更前:</h4>
      <pre>{{ diff.before }}</pre>
      <h4>变更后:</h4>
      <pre>{{ diff.after }}</pre>
      <h4>证据引用:</h4>
      <p>{{ diff.evidenceSnippet }}</p>
    </div>
  </el-dialog>
</template>

<script setup>
import * as jsondiffpatch from 'jsondiffpatch';
// 简单版本直接展示 before/after, 复杂版本用 jsondiffpatch.formatters.html
</script>
```

### 3.6 RI-68 旧系统 Excel 导入

```java
// controller/ImportController.java
@RestController
@RequestMapping("/api/admin/import")
@PreAuthorize("hasRole('ADMIN')")
public class ImportController {
    
    @PostMapping(value = "/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportBatch> importExcel(
        @PathVariable String type,  // project | material | proposal | fact
        @RequestParam("file") MultipartFile file
    ) {
        ImportBatch batch = importService.importExcel(type, file);
        return ResponseEntity.ok(batch);
    }
    
    @GetMapping("/{batchId}/errors")
    public ResponseEntity<List<ImportError>> getErrors(@PathVariable Long batchId) {
        return ResponseEntity.ok(importService.getErrors(batchId));
    }
}

// service/ImportService.java
@Service
public class ImportService {
    
    public ImportBatch importExcel(String type, MultipartFile file) {
        ImportBatch batch = new ImportBatch();
        batch.setType(type);
        batch.setTotal(0);
        batch.setSuccess(0);
        batch.setFailed(0);
        batch.setCreatedBy(getCurrentUserId());
        batch.setCreatedAt(LocalDateTime.now());
        batch = batchRepo.save(batch);
        
        try (SXSSFWorkbook wb = new SXSSFWorkbook(new ByteArrayInputStream(file.getBytes()));
             XSSFSheet sheet = (XSSFSheet) wb.getSheetAt(0)) {
            
            int total = 0, success = 0, failed = 0;
            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) {
                total++;
                try {
                    switch (type) {
                        case "project" -> importProject(sheet.getRow(rowIdx));
                        case "material" -> importMaterial(sheet.getRow(rowIdx));
                        case "proposal" -> importProposal(sheet.getRow(rowIdx));
                        case "fact" -> importFact(sheet.getRow(rowIdx));
                    }
                    success++;
                } catch (Exception e) {
                    failed++;
                    ImportError err = new ImportError();
                    err.setBatchId(batch.getId());
                    err.setRow(rowIdx);
                    err.setColumn(0);
                    err.setErrorMsg(e.getMessage());
                    errorRepo.save(err);
                }
            }
            
            batch.setTotal(total);
            batch.setSuccess(success);
            batch.setFailed(failed);
            batch = batchRepo.save(batch);
            
        } catch (Exception e) {
            // 整批失败, 标记 batch 状态
            log.error("Import batch {} failed: {}", batch.getId(), e.getMessage());
            failureLogService.log("import.batch", "BATCH_FAILED", e.getMessage(), "");
        }
        
        return batch;
    }
}
```

### 3.7 RI-69 数据脱敏视图（D-1 5 角色 + MOD-02 RBAC）

```java
// service/MaskingService.java
@Service
public class MaskingService {
    
    public MaskedProjectResponse mask(Project project, User viewer) {
        boolean isSensitive = !viewer.getSensitiveViewEnabled() 
            && rbacService.hasRole(viewer.getId(), "COMMITTEE");
        
        if (!isSensitive) {
            return MaskedProjectResponse.unmasked(project);
        }
        
        // 脱敏
        String displayName = maskName(project.getCustomerName());  // "张**"
        String displayAmount = maskAmount(project.getAmount());     // "***万"
        
        return MaskedProjectResponse.masked(project, displayName, displayAmount);
    }
    
    private String maskName(String name) {
        if (name == null || name.length() <= 1) return name;
        return name.charAt(0) + "**";
    }
    
    private String maskAmount(Double amount) {
        if (amount == null) return null;
        return "***万";  // 简化, 实际可按区间
    }
}

// controller/ProjectController.java (扩)
@PostMapping("/{id}/unmask-request")
public ResponseEntity<?> requestUnmask(@PathVariable Long id, Authentication auth) {
    Long userId = getUserId(auth);
    // 1. 写 audit_log.type=SENSITIVE_VIEW
    auditLogService.logSensitiveView(userId, "project", id, "申请脱敏查看");
    // 2. 通知 admin
    notificationService.notifyAdmin("用户申请脱敏查看项目 " + id);
    return ResponseEntity.ok(Map.of("unmaskRequestUrl", "/api/projects/" + id + "?unmask=true&token=..."));
}
```

---

## §4 验收

### 4.1 编译验证

```bash
cd /workspace/projects-online
mvn compile -DskipTests -B
npm run build
# 期望：两个 BUILD SUCCESS
```

### 4.2 单元测试

```bash
mvn test -B \
  -Dtest='ProjectBoardServiceTest,NotificationServiceTest,ExportServiceTest,PreviewServiceTest,ImportServiceTest,MaskingServiceTest,ProjectFactEventServiceTest'
# 期望：≥ 35 测例全过
```

### 4.3 端到端验证（手动 + integration test）

| RI | 场景 | 期望 |
|---|---|---|
| RI-62 | GET `/api/projects/board?view=kanban&region=江苏` | 返回按 stage 分组的看板 |
| RI-62 | GET `/api/projects/board?view=table&sort=amount&order=desc&page=1&size=20` | 返回表格分页 |
| RI-63 | GET `/api/notifications?unread=true` | 返回未读列表 |
| RI-63 | PATCH `/api/notifications/{id}/read` | 200 |
| RI-64 | GET `/api/projects/1/export?format=pdf` | 返回 PDF 流 |
| RI-64 | GET `/api/projects/export?format=xlsx&type=materials` | 返回 Excel 流 |
| RI-65 | GET `/api/materials/1/preview` | 返回 PDF 流 |
| RI-65 | Word 文件 + 前端 mammoth | HTML 渲染 |
| RI-66 | GET `/api/projects/1/fact-events/1/diff` | 返回 before/after |
| RI-68 | POST `/api/admin/import/project` multipart | 返回 batch + errors |
| RI-69 | 委员 GET `/api/projects/1` | `masked: true, displayName: '张**'` |
| RI-69 | 委员 POST `/api/projects/1/unmask-request` | 200 + audit_log + notification |

### 4.4 前端验收

```bash
cd /workspace/projects-online/frontend
npm run build
# 期望：0 错
```

**关键场景**：
- 顶栏 NotifBell 显示未读数 badge
- 通知中心全屏 `/notifications`
- 项目看板 3 视图切换
- 材料列表点文件名 → PreviewFrame 弹窗
- 事实时间线 → DiffViewer 弹窗
- 委员视图 → MaskedField + 申请查看按钮

### 4.5 完工 checklist

- [ ] 8 新 + 5 改后端全部 commit
- [ ] 8 新 + 5 改前端全部 commit
- [ ] pom.xml 加 OpenPDF + POI
- [ ] package.json 加 pdfjs-dist + mammoth + jsondiffpatch + dayjs
- [ ] `mvn compile` 0 错
- [ ] `npm run build` 0 错
- [ ] `mvn test` ≥ 35 测例过
- [ ] §4.3 关键场景全过
- [ ] 改 `TASKS.md` 状态 → `已完成`

---

## §5 踩坑预警

### 5.1 OpenPDF 跟 iText API 不同

D-4 选 OpenPDF 不是 iText。`com.github.librepdf:openpdf`，**不是** `com.itextpdf:itextpdf`。OpenPDF 沿用 iText 4.x LGPL 包名 `com.lowagie.text.*`。

### 5.2 导出要审计（RI-35/59）

任何 export 端点都要调 `auditLogService.logExport(userId, type, entityId)`，**严禁**直接返回流不审计。否则 §13.2.4 验收会挂。

### 5.3 通知 4 类来源跟 `todo` 表不冲突

RI-63 §13.3.2 明确：通知 ≠ 待办。`todo` 表是行动项，`notification` 表是事件流。**不要**复用 `todo`。

### 5.4 看板聚合查询性能

9 列里有 `COUNT(DISTINCT pr.id)` / `COUNT(DISTINCT t.id)`，大表可能慢。**v1.1 不做性能优化**（< 100 项目可接受），v2 多项目时考虑物化视图。

### 5.5 附件预览 PDF 缓存

PDF 直接 `<iframe>` 流式渲染没问题，但**大 PDF（> 50MB）会卡浏览器**。PreviewService 加 `Content-Length` 检查，> 50MB 拒绝 + 提示"请下载查看"。

### 5.6 Excel 导入字段校验必填

不要相信 Excel 列名顺序，用 `Row.getCell(idx, MissingCellPolicy.CREATE_NULL_AS_BLANK)` + 显式列名映射。失败行必须写 `import_error`，**不要**静默跳过。

### 5.7 脱敏视图不破 admin（零回归）

D-1 双轨：admin 走 `user.role_id=admin`（v1.0 兼容），永远 `masked=false`。**测试**必须覆盖：同一项目，admin 看 unmasked，委员看 masked。

### 5.8 前端 mammoth 转 Word 可能乱

mammoth 对复杂表格/图片支持差，**v1.1 期允许**：复杂 Word 提示"建议下载查看"。README 加说明。

### 5.9 通知 30s 轮询不能用 SSE

D-3 + 沿用 v1.0 §0 风险 C-3：不引 SSE，30s 轮询。**不要**为了"实时"引 WebSocket。

### 5.10 路由别忘了 role meta

```ts
{
  path: '/recycle-bin',
  component: () => import('@/views/RecycleBin.vue'),
  meta: { title: '回收站', icon: 'Delete', roles: ['ADMIN'] }
}
```

`router.beforeEach` 校验 user 是否在 `roles` 列表里，否则 403 重定向到 Dashboard。

---

## §6 接口契约

### 6.1 给 MOD-05（前端集成）

- `/api/projects/{id}` 响应体新增 `masked / displayName / displayAmount / unmaskRequestUrl`
- `/api/notifications/unread=true` 30s 轮询
- `/api/projects/board?view=*` 看板接口
- `/api/materials/{id}/preview` PDF/图片/文本流
- `/api/projects/{id}/fact-events/{eventId}/diff` JSON tree diff
- `/api/admin/import/{type}` multipart 上传

### 6.2 给 MOD-06（文档/测试）

- 35+ 测例 + 11 个端到端场景（§4.3）
- 6 个新前端 View + 4 个新 Component 全集成测试覆盖

---

*本模块由全栈 agent 接手。MOD-01 + MOD-02 + MOD-03 完工后开工。*
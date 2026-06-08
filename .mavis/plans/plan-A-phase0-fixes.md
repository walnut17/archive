# Plan A: Phase 0 — 阻塞性缺陷修复

> **状态**: 准备启动(等用户拍板)
> **优先级**: 🔴 P0(必须先修,不然功能不工作)
> **工作量**: 1-2 个 commit,30-45 分钟
> **依赖**: M0~M2 已落 minimax 分支
> **互斥**: 不与 B/C/D/E/F 并行(优先)

## 必读文档(启动前)

1. `docs/REQUIREMENTS-v1.md`(业务背景)
2. `docs/ARCHITECTURE-v2.md`(架构 + 模块清单)
3. `docs/DB-SCHEMA-v2.md`(v2 schema)
4. `docs/DEV-STANDARDS.md`(本规范)
5. `docs/TEAM-ARCHIVE.md`(环境)
6. `docs/LESSONS-LEARNED.md`(踩坑)
7. `SUPPLEMENTARY-REQUIREMENTS.md` § Phase 0

## 范围(6 个子项)

### A-1. P0-1 前端解包错(已修 4 处,但前端还要扫一遍)

**目标**: 扫所有 `.vue` 文件,确认**没有**残留的 `resp.data` 错位

**具体操作**:
```bash
cd backend
grep -rn "resp\.data\." frontend/src/
grep -rn "as any" frontend/src/
```

**已修清单**(commit `3b487ef`):
- `ProjectDetail.vue:37-38`
- `ProposalDetail.vue:44-45`
- `ProjectForm.vue:31-32`
- `ProposalDetail.vue:93-94`(listVersions)
- `ProposalDetail.vue:139-140`(listSections)

**这次还要查**:
- `Knowledge.vue`(M2 新加)
- `Login.vue`
- `Layout.vue`
- 任何新写的组件

**验收**: 浏览器打开每个页面,功能正常,Console 无错。

### A-2. P0-2 FULLTEXT 索引要进 init.sql

**目标**: 不依赖单独的 M2-fulltext-index.sql,**集成到 init.sql**

**改动**:
- `backend/src/main/resources/db/init.sql` 的 `material_version` 表定义里:
  - 加 `parsed_text LONGTEXT` 字段
  - 加 `FULLTEXT INDEX ft_parsed_text (parsed_text) WITH PARSER ngram`
- 删除或标 deprecated `db/migration/M2-fulltext-index.sql`

**验收**: 全新建库 + 跑 init.sql,`SHOW INDEX FROM material_version` 看到 `ft_parsed_text`。

### A-3. P0-3 分页 0/1-based 修复

**目标**: Element Plus 的 `el-pagination` 1-based → 转 0-based 给后端

**改动**:
- `frontend/src/views/ProjectList.vue`:
  - `query.page` 初始值改成 `1`
  - 调 `listProjects({ ...query.value, page: query.value.page - 1 })`
- 扫所有其他分页页面(Material / Proposal / Todo / Audit),统一加 `- 1`

**验收**: 浏览器点分页第 2 页 → 看到第 2 页数据(不是第 3 页)。

### A-4. P0-4 AuditorAware 实现

**目标**: 填 `@CreatedBy` / `@LastModifiedBy`,不再是 null

**改动**:
- 新增 `backend/src/main/java/com/archive/config/AuditorAwareImpl.java`
- `ArchiveApplication.java` 加 `@Bean AuditorAware<String> auditorAware()`
- 实现从 `SecurityContextHolder` 取当前用户名,未认证返回 `"system"`

**验收**: 创建项目后,DB 看 `created_by` 字段 = `admin`。

### A-5. P0-5 Layout.vue 侧边栏图标 + 菜单去重

**目标**: 图标能正常渲染,菜单不重复

**改动**:
- `frontend/src/views/Layout.vue`:
  - 顶部 `<script setup>` 加 `import { DataLine, Folder, Document, AlarmClock, SetUp } from '@element-plus/icons-vue'`
  - 检查 `/knowledge` 菜单项是否有重复
  - 检查所有 icon 是否被 import

**验收**: 浏览器看侧边栏,图标正常显示,无重复菜单。

### A-6. P0-6 Knowledge.vue 回车发送

**目标**: 输入框按 Enter 发送,Shift+Enter 换行

**改动**:
- `frontend/src/views/Knowledge.vue`:
  - `<el-input>` 加 `@keydown.enter.prevent="!$event.shiftKey && onAsk()"`

**验收**: 浏览器问答页,输入问题后按 Enter,直接发送。

## 提交规范

每个子项一个 commit:
```
fix(frontend,A-1): 扫所有 .vue 文件,清除残留 resp.data
fix(backend,A-2): init.sql 集成 FULLTEXT 索引 + parsed_text
fix(frontend,A-3): 分页 1-based 转 0-based
feat(backend,A-4): 实现 AuditorAware,从 SecurityContext 取用户名
fix(frontend,A-5): Layout.vue 导入图标 + 去重 /knowledge 菜单
fix(frontend,A-6): Knowledge.vue 回车发送问题
```

每个 commit **单独 push** 到 `minimax`,**不**打包。

## 自测

每个子项完工后:
1. `mvn compile -DskipTests -B -o`(后端改动)
2. `npm run build`(前端改动)
3. 浏览器跑一遍相关页面,贴结果
4. 填到 `docs/M2-TEST-TASKS.md` 或新开 `docs/PHASE-0-TEST-TASKS.md`

## 交回物

完工后向 owner(Mavis)交:
1. ✅ 6 个 commit + push 链接 / commit hash
2. ✅ `mvn compile` + `npm run build` 通过截图
3. ✅ 每个子项的浏览器自测结果
4. ✅ `docs/LESSONS-LEARNED.md` 加新增的坑(如有)

## 不在本 plan 范围

- ❌ P1-1 ~ P1-4(在 Plan B)
- ❌ P2-* / P3-*(在 Plan C/D)
- ❌ 任何重构、架构调整

## 风险/注意

- ⚠️ A-1 改前端:**改完必须 grep 一遍**残留 `as any`(避免再出隐藏 bug)
- ⚠️ A-2 改 init.sql:**备份一份**给老库用(`init.sql.v1`)
- ⚠️ A-4 加 AuditorAware: 测一下未登录情况(应该返回 "system")

# Plan E: Phase 3 — 权限细化 + 字典管理 UI + 抽取/对比方法 UI

> **状态**: 准备启动(等 Plan C/D 完成)
> **优先级**: 🟡 P3
> **工作量**: 6-8 个 commit,1-2 天
> **依赖**: Plan C 完成
> **互斥**: 可与 F 并行做不同模块

## 必读文档

1. `docs/REQUIREMENTS-v1.md` § 3 角色与权限 + § 6/7 扩展机制
2. `docs/ARCHITECTURE-v2.md`
3. `docs/DB-SCHEMA-v2.md` § dict_type/dict_item
4. `docs/DEV-STANDARDS.md`

## 范围(5 个子项)

### E-1. P3-1 登录限流

**目标**: 防止爆破,5 次/分钟/IP,失败 5 次锁 15 分钟

**后端**:
- `security/LoginRateLimiter.java`(组件)
  - 用 ConcurrentHashMap 存 IP → (尝试次数, 锁定到期时间)
  - 每次 login 前检查
  - 失败 +1,成功重置
- `AuthService.login()` 集成

**测试**:
- 单元测试 6 次连续失败 → 第 6 次拒绝

**验收**: 1 分钟内 5 次失败 → 第 6 次返回 429。

### E-2. P3-2 RBAC 权限细化

**目标**: admin / user 角色粒度控制

**后端**:
- `security/SecurityConfig.java`: 端点加 `hasRole('admin')` / `hasAuthority(...)`
- 端点分组:
  - admin only:
    - `GET/POST/PUT/DELETE /api/dict/*`
    - `GET/POST/PUT/DELETE /api/extraction-methods/*`
    - `GET/POST/PUT/DELETE /api/comparison-methods/*`
    - `GET /api/audit-logs`
  - admin + user:
    - `GET /api/dict/options`(公开字典查询,下拉框用)
    - 所有 Project/Material/Todo CRUD

**前端**:
- `store/auth.ts`: 存 role
- `router/index.ts`: 路由守卫
- 菜单根据 role 隐藏

**测试**:
- 集成测试: user 调 admin API → 403
- user 看不到"参数管理"菜单

**验收**: admin 看得到所有菜单;user 看不到参数管理。

### E-3. P3-3 文件去重(SHA-256)

**目标**: 同 material 下,文件 hash 一致提示而不是创建新版本

**后端**:
- `service/MaterialVersionService.upload()`:
  - 计算上传文件的 SHA-256
  - 在同一 material 的 version 里查
  - 若存在 → 抛 `IllegalStateException("文件已存在,版本 v{no}")`,HTTP 409
  - 不同 material → 允许上传

**测试**:
- 单元测试: 同 material 上传 2 次相同文件,第二次报错

**验收**: 同材料上传相同文件,后端 409,前端弹错。

### E-4. P3-4 SearchResult 片段提取改进

**目标**: snippet 提取更准,优先匹配问题关键词密集区

**后端**:
- `service/KnowledgeSearchService.java`:
  - 改 `extractSnippet()` 函数
  - 用 MySQL `LOCATE()` 找问题里**所有**关键词位置
  - 选密度最高的 200 字符窗口

**测试**:
- 单元测试: 多种问题长度

**验收**: 浏览器问答结果,引用片段跟问题高度相关。

### E-5. P3-5 Logout 清除浏览器历史

**目标**: 退出后回不到登录后的页面

**前端**:
- `Layout.vue` 的 `onLogout()`:
  ```ts
  // 原: router.push('/login')
  // 改: window.location.replace('/login')
  ```

**验收**: 退出后浏览器后退按钮,回不到 dashboard。

## 额外:管理 UI(放这里)

### E-6. 字典管理 UI(admin)

**前端**:
- `views/AdminDict.vue`:
  - 左侧:字典分类列表
  - 右侧:选中分类的字典项表格
  - CRUD 按钮(新增/编辑/删除/启用禁用)
  - `is_system=true` 的字典禁止删
- 路由:`/admin/dict`
- `Layout.vue` 侧边栏:仅 admin 看到"参数管理 → 字典管理"
- `api/archive.ts` 删硬编码数组(项目状态/分类等),改用 `getDictOptions(typeCode)`

**后端**:
- `controller/DictController.java`:已有
- API:
  - `GET /api/admin/dict-types`
  - `GET /api/admin/dict-items?typeCode=xxx`
  - `POST/PUT/DELETE /api/admin/dict-items/{id}`
  - `GET /api/dict/options?typeCode=xxx`(公开)

**验收**: admin 登录 → 侧边栏看到"参数管理" → 字典管理可 CRUD。

### E-7. 抽取方法 UI(admin)

**前端**:
- `views/AdminExtraction.vue`:
  - 表格 + CRUD
  - 字段:名称 / 目标字段(下拉)/ prompt 模板(textarea)/ 启用
  - 预置的 `is_builtin=true` 禁用删除按钮
- 路由:`/admin/extraction`
- 菜单:仅 admin

**验收**: admin 加 1 个抽取方法,立刻对新上传材料生效。

### E-8. 对比方法 UI(admin)

**前端**:
- `views/AdminComparison.vue`:
  - 表格 + CRUD
  - 字段:名称 / prompt 模板 / 启用
  - `is_builtin=true` 禁用删除

**验收**: admin 加 1 个对比方法,申请报告上传时自动跑。

### E-9. 触发规则 UI

**前端**:
- `views/AdminTrigger.vue`(或嵌在 ProjectDetail 里):
  - 项目级规则列表
  - 表格:规则名 / 条件 / 动作 / 启用
  - 弹窗编辑(JSON 简化版表单:条件下拉选分类,动作填 title/priority/due_days)
  - **不**暴露 JSON 编辑器(避免用户写错)

**验收**: admin 在项目级加 1 条规则,材料上传自动触发。

## 提交规范

```
feat(backend,E-1): LoginRateLimiter 5次/分钟/IP
feat(backend,E-2): SecurityConfig 加 RBAC 端点限制
feat(backend,E-3): MaterialVersionService 文件 SHA-256 去重
feat(backend,E-4): KnowledgeSearchService 片段提取改进
fix(frontend,E-5): Layout.vue logout 改 window.location.replace
feat(frontend,E-6): AdminDict.vue 字典管理页 + 删硬编码
feat(frontend,E-7): AdminExtraction.vue 抽取方法管理
feat(frontend,E-8): AdminComparison.vue 对比方法管理
feat(frontend,E-9): 触发规则管理 UI
```

## 自测

1. `mvn compile / test`
2. `npm run build`
3. admin / user 浏览器测权限
4. 字典管理加项,看是否在 Project 表单下拉出现

## 交回物

1. ✅ 所有 commit + push
2. ✅ RBAC 测试报告
3. ✅ 4 个 admin 页面的浏览器截图
4. ✅ `docs/PHASE-3-TEST-TASKS.md`

## 不在本 plan 范围

- ❌ 移动端(本项目不做)
- ❌ 数据导出(后续)
- ❌ 性能优化(Plan F)

## 风险/注意

- ⚠️ E-2 RBAC: 测试**所有端点**别漏
- ⚠️ E-6 删硬编码:**扫所有用到** `projectCategoryOptions` 之类的页面
- ⚠️ E-9 触发规则 UI:JSON 字段**做表单化**,不暴露原文编辑器

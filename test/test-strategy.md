# 测试策略

> 适用范围：投委会档案管理系统后端测试
> 最后更新：2026-06-11（plan-2026-06-11-test-governance）

---

## 1. 测试层次

```
单元测试（JUnit 5）
    ↓
集成测试（@SpringBootTest + H2）
    ↓
端到端手工验收（staging MySQL + 浏览器）
```

---

## 2. 集成测试（CI 用）

**环境**：H2 内存数据库 + `@SpringBootTest` + MockBean GlmService

### 2.1 覆盖范围

| 模块 | 覆盖内容 | 测例数 |
|------|---------|--------|
| CRUD | project / proposal / material / todo | ~10 |
| RBAC | 5 角色双轨查询 | ~4 |
| 审计 | audit_log 写入与查询 | ~3 |
| 通知 | create / read / markAllRead | ~3 |
| 导出 | PDF / Excel 生成 | ~2 |
| 脱敏 | 委员脱敏 / admin 可见 | ~2 |
| Agent | ReAct 基本流、工具调用 | ~3 |
| 软删 | deleted_at 过滤 | ~3 |
| 乐观锁 | version 字段存在 | ~2 |
| 看板 | table / kanban 视图 | ~2 |
| **合计** | | **~45** |

### 2.2 不覆盖

- MySQL FULLTEXT 索引（H2 不支持 `MATCH AGAINST`）
- MySQL 方言 / 触发器 SIGNAL（H2 不支持 `SIGNAL SQLSTATE`）
- 真实 LLM 调用（GlmService 一律 mock）
- Caddy 反代 / HTTPS 终止

### 2.3 缺口处理

H2 测不过的功能统一标记 `@DisabledIf(expression = "#{systemProperties['test.mysql'] == null}")`，
由 staging MySQL 补测。

---

## 3. Staging 手工验收（上线前）

### 3.1 补测清单

| 项目 | 验证方式 | 关联 |
|------|---------|------|
| FULLTEXT 检索 | 知识库提问 → 返回材料片段 | M2 |
| 触发器防篡改 | UPDATE project_fact_event 非白名单字段 → 报错 | MOD-01 |
| 迁移脚本顺序执行 | 从头跑所有 I-RI-*.sql → 0 错 | MOD-01 |
| 真实 GLM Agent | 提问 → 走 Agent 路径 → 返回答案 | Plan I |
| Caddy 反代 | HTTPS → 前端 SPA → /api 反代 | 部署 |

### 3.2 补测工具

```bash
# FULLTEXT 快速验证
mysql -u root archive_db -e "
SELECT id, title FROM material_version
WHERE MATCH(parsed_text) AGAINST('风险' IN BOOLEAN MODE)
LIMIT 5;"

# 触发器验证
mysql -u root archive_db -e "
UPDATE project_fact_event SET fact_value='篡改' WHERE id=1;"
# 期望: ERROR 1644 (45000)
```

---

## 4. 与部署流程的关系

- `docs/v1.1-DEPLOY-GUIDE.md` §5 验收 checklist 包含 staging 补测
- 集成测试通过 + staging 补测通过 = 可上线

---

*撰写人：Sisyphus · 2026-06-11*

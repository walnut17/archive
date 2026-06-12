# AT-XXX — <测试案例标题>

> **TASKS.md**：`AT-XXX`  
> **案例状态**：`未执行` / `已通过` / `失败-已报 bug`  
> **关联**：RI-N / MOD-XX / 可选 [`test-to-settle/old/ACCEPTANCE-GUIDE`](../test-to-settle/old/ACCEPTANCE-GUIDE.md)

---

## 1. 用例说明

| 字段 | 内容 |
|---|---|
| **目的** | 验证什么能力 |
| **前置** | 环境、数据、账号、服务已启 |
| **类型** | 单元 / 集成 / E2E / 构建 |

### 1.1 步骤

1. …
2. …

### 1.2 预期结果

- …

### 1.3 命令（可选）

```bash
# 按案例填写
cd backend && mvn test -Dtest=… -B
```

---

## 2. 占用与完工（同步 TASKS.md）

| 字段 | 内容 |
|---|---|
| **TASKS 条目** | `AT-XXX`（PASS 后**删行**） |
| **状态** | 执行中见 TASKS；PASS 后仅本文件 §3 留痕 |

---

## 3. 执行历史

> **通过**：在表末**追加**一行，结果写 `PASS` / **已成功**。  
> **失败**：追加 `FAIL`，并新建 [`test-to-settle/test_bug-*.md`](../test-to-settle/test_bug-TEMPLATE.md)。

| 时间 | Agent | 环境 | 代码基线 | 结果 | 备注 / 链接 |
|---|---|---|---|---|---|
| | | | | | |

---

*模板 · 见 [test_task/README.md](./README.md)*

# 构建/测试日志 — `test/logs/`

> **自动生成**，本地留存排错用，**不进 git**（见根 `.gitignore`）。

**上级目录**：[`../README.md`](../README.md)（bug 流程）· [`../../test_task/`](../../test_task/README.md)（案例 PASS 历史写在案例文件，不是这里）

---

## 推荐输出路径

在仓库根执行：

```powershell
cd backend
mvn compile -DskipTests -B 2>&1 | Tee-Object -FilePath ..\test\logs\mvn-compile.log
mvn test -Dtest=V11IntegrationTest -B 2>&1 | Tee-Object -FilePath ..\test\logs\mvn-test.log
```

Linux / bash：

```bash
cd backend
mvn compile -DskipTests -B 2>&1 | tee ../test/logs/mvn-compile.log
mvn test -Dtest=V11IntegrationTest -B 2>&1 | tee ../test/logs/mvn-test.log
```

---

## 与相邻文件

| 文件 | 用途 |
|---|---|
| **`test/logs/*.log`** | 原始命令输出（gitignore） |
| **`test/old/VERIFICATION-REPORT.md`** | 历史验证报告（只读） |
| **`test_task/*.md` §3** | AT 案例 **PASS** 时写执行历史（可 commit） |
| **`test/test_bug-*.md`** | 案例 **FAIL** 时建 bug 入口 |

---

*协作架构见 [`MULTI-AGENT-REPO-ARCHITECTURE.md`](../../MULTI-AGENT-REPO-ARCHITECTURE.md)*

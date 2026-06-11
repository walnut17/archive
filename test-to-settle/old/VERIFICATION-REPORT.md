# v1.1 后端 mvn 验证报告（接手 agent 沙箱）

> 执行：阿根廷  
> 日期：2026-06-11  
> 沙箱：Windows 10 (win32 10.0.26200)，本机 Temurin JDK + 系统 Maven  
> 基线：`3ec2fb0`（pull 后验证；PM 沙箱工具集已在仓库）

## 1. 环境

| 项 | 实测 |
|---|---|
| 操作系统 | Windows 10.0.26200 (amd64) |
| JDK | OpenJDK **17.0.19** Temurin (`C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot`) |
| Maven | Apache Maven **3.8.8**（系统安装；仓库 bundled 为 3.9.9，本机直接用 `mvn`） |
| 命令 | 本机 `mvn`（未用 `backend/tools/mvn-tools.sh`，Windows 环境） |
| cacerts | N/A（Windows JDK 自带，Java 工具链正常） |

```text
openjdk version "17.0.19" 2026-04-21
OpenJDK Runtime Environment Temurin-17.0.19+10 (build 17.0.19+10)
Apache Maven 3.8.8
Java version: 17.0.19, vendor: Eclipse Adoptium
```

## 2. mvn compile 结果

- **命令**：`cd backend && mvn compile -DskipTests -B`
- **结果**：**BUILD SUCCESS**
- **编译产物**：`backend/target/classes/`
- **耗时**：约 **3.2 s**（修复后二次编译；首次含源码变更约 15 s）

关键输出：

```text
[INFO] BUILD SUCCESS
[INFO] Total time:  ~3 s
```

### 2.1 修复的 P0 编译问题（阿根廷）

| 文件 | 问题 | 处理 |
|---|---|---|
| `NetworkDictService.java` | `JsonNode` 误用 `asText()` 赋值 | 改为 `String` |
| `RbacService.java` | `JdbcTemplate.query` 方法引用歧义 | 显式 `RowMapper` |
| 多个 Entity | MySQL `columnDefinition=JSON/TEXT` H2 不兼容 | `@JdbcTypeCode` / `@Lob` |
| Cron 表达式 | Spring 6 字段 cron 需 6 段 | `0 0 2 * * *` 等 |

## 3. mvn test (V11IntegrationTest) 结果

- **命令**：`cd backend && mvn test -Dtest=V11IntegrationTest -B`
- **测例数**：**44**（代码库当前 `@Test` 数量；spec 写 45，以实测为准）
- **通过**：**44**
- **失败**：**0**
- **错误**：**0**
- **跳过**：**0**
- **耗时**：约 **36.3 s**

关键输出：

```text
[INFO] Tests run: 44, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 3.1 修复的测试阻塞项（阿根廷）

| 测例 / 场景 | 根因 | 处理 |
|---|---|---|
| `mod04_maskingCommitteeView` | 用户 `roleId=admin` 覆盖 committee 角色 | 委员用户改用 `committeeRole.getId()` |
| `mod05_knowledgeSearchDegradesWithoutLlm` | H2 无 `MATCH()` + 只读事务 rollback-only | FULLTEXT try-catch + 去掉 `search()` 外层只读事务 |
| `scenario7_MultiTurnConversationPreservesContext` | H2 不支持 FULLTEXT | `FindProjectTool` FULLTEXT 失败降级 LIKE |
| 多测例 | H2 `failure_log` / `user_role` 缺表 | `ensureV11JdbcTables()` 补 DDL |
| 多测例 | H2 保留字 `USER`、引号标识符 | `application-test.yml` 调 H2 URL / `globally_quoted_identifiers` |

## 4. 已知问题 / 说明

1. **测例数量 44 vs spec 45**：当前 `V11IntegrationTest` 仅 44 个 `@Test`，全部通过；若 PM 期望 45，需核对是否遗漏用例或 spec 笔误。
2. **H2 vs MySQL**：集成测试在 H2 内存库运行；FULLTEXT、`failure_log` 等 MySQL 特性已通过降级/补表适配，**生产仍应用 MySQL 8 复验 FULLTEXT**。
3. **本机 Maven 3.8.8**：与仓库 bundled 3.9.9 版本不同，本次 compile/test 均 SUCCESS。
4. **NetworkDict 外网**：测试中 `NetworkDictService` 访问百科源可能超时（WARN），不影响断言。

## 5. 结论

- ✅ **v1.1 后端在本机 JDK 17 + Maven 环境下 compile 与 V11IntegrationTest(44/44) 全部通过**
- ✅ 阻塞 PM 沙箱的 mvn 验证已完成；代码含必要 H2/编译 P0 修复，可交 PM 审阅后部署

---

*阿根廷 | 2026-06-11 | T-v1.1-42 交付*

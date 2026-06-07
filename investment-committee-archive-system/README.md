# 投委会档案管理系统 — 架构方案归档

> 立项日期:2026-06-05
> 状态:**待 P0 启动**(等用户拍 3 个决策点 + 智谱 API key)
> 维护者:Mavis(架构设计)+ 用户本人(开发)

---

## 这是什么

投委会档案管理系统的**完整架构方案**。场景:Windows 单机服务器(32GB,CPU 经常空)+ 单人开发 + 内网同事浏览器访问 + 50GB 文档。

## 文档演进(三版)

| 版本 | 文件 | 何时写 | 状态 |
|---|---|---|---|
| **v1** | `architecture-v1-full.md` | 2026-06-05 初始版 | ❌ **已废弃** — 按"中型金融机构、双机主备"设计,严重跑偏 |
| **v2** | `architecture-v2-lite.md` | 用户说"32GB 内存、单人"后 | ⚠️ **过渡版** — 已经定位为单机轻量,但 RAG 选型还在向量化纠结 |
| **v3** | `architecture-v3-final.md` | 完整定稿(智谱 + 不向量化 + 智谱 GLM-4V OCR) | ✅ **当前定稿** — 单人单机方案 |

**请以 v3 为准**。v1/v2 保留作为演进记录,不要参考其内容做新决策。

## v3 核心决策(一句话)

- **后端**:Java 17 + Spring Boot 3.3 单体
- **数据库**:MySQL 8.0.16(已有)+ FULLTEXT 索引(主力检索,找准确信息)
- **知识库**:**不向量化**,FULLTEXT top 20 + 智谱 GLM-4-Flash LLM 智能重排(找相似语义由 LLM 推理)
- **大模型**:智谱 GLM-4-Flash(免费,问答/摘要/时点抽取)+ 智谱 GLM-4V(扫描件 OCR)
- **文档解析**:Apache Tika(docx/xlsx/文本 PDF)+ 智谱 GLM-4V(扫描件多模态)
- **规则引擎(5 号需求)**:Aviator 5.x(轻量,不上 Drools)
- **脱敏**:正则(证件号)+ HanLP NER(人名)
- **部署**:Spring Boot JAR + Caddy + MySQL,WinSW 包成 Windows 服务,单机 3 进程
- **监控**:Spring Boot Actuator + cron 邮件告警(不上 Prometheus+Grafana)
- **备份**:每日 mysqldump + robocopy 同步到 E 盘
- **总月成本**:基本 0 元(GLM-4-Flash 免费,GLM-4V 一次性建库 50-100 元)

## 文件清单(本子目录)

```
investment-committee-archive-system/
├── README.md                       # 本文件
├── .gitignore.example              # .gitignore 模板
├── architecture-v1-full.md         # v1,已废弃,留作演进记录
├── architecture-v2-lite.md         # v2,过渡版
├── architecture-v3-final.md        # v3,**当前定稿**
└── config/
    ├── README.md                   # 配置文件使用说明
    └── config.example.json         # 配置模板(用户复制为 config.json 填值)
```

> **配置约定**:`config/config.json` 是真实配置,**不进 Git**(加 .gitignore);`config.example.json` 是模板,可以进 Git。详见 `config/README.md`。

## 后续待办(用户决策点)

进入 P0 启动前,需要用户拍 3 件事:

1. **大模型 API 选 A/B/C 哪一套**:
   - A. 智谱 GLM-4-Flash + GLM-4V(默认推荐,完全免费,中文 SOTA)
   - B. 自写 RAG + 本地 bge-m3 + Qdrant(全内网,零外网,4-5 天工作量)
   - C. 混合 FULLTEXT + 智谱 Embedding(2-3 天)

2. **历史档案盘点**:IT 能否在 1 周内提供文件服务器清单 + 命名规范?

3. **评估集建设**:业务方投入 5-10 人天标注 30-50 条 QA(用户自己 1 人可标)

## 启动条件

3 个决策点 + 智谱 API key + MySQL 连接信息 → 即可启动 M0 基建(2-3 天,出最小可登录 demo)。

---

*本文档由 Mavis 起草,用户审阅。*

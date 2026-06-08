# 投委会档案管理系统 - 前端

> Vue 3 + Vite 5 + TypeScript + Element Plus + Pinia
> 状态:M0 基建完成,登录页 + 空框架 + 路线图

---

## 1. 准备

| 工具 | 版本 | 验证 |
|---|---|---|
| Node.js | 20 LTS | `node -v`(v20.x) |
| npm | 10+ | `npm -v` |

## 2. 安装依赖

```bash
cd frontend
npm install
```

## 3. 开发模式(连后端)

```bash
# 确保后端在 :8080 跑着
npm run dev
```

打开 http://localhost:5173

Vite 会把 `/api/*` 代理到 `http://localhost:8080`,所以你直接登录就行。

**默认账号**:admin / admin123

## 4. 生产构建

```bash
npm run build
# 产物在 dist/ 目录

# 拷贝到 Caddy 服务的静态目录
mkdir -p D:\archive\apps\frontend\dist
cp -r dist/* D:\archive\apps\frontend\dist\
```

Caddyfile 已经配好从 `D:\archive\apps\frontend\dist` 托管前端,直接访问 `https://archive.internal.example.cn` 即可(SPA 路由由 Caddy 的 `try_files` 处理)。

## 5. 目录结构

```
frontend/
├── package.json
├── vite.config.ts
├── tsconfig.json
├── tsconfig.node.json
├── index.html
├── .gitignore
├── README.md
└── src/
    ├── main.ts                     # 启动入口
    ├── App.vue                     # 顶层 RouterView
    ├── assets/
    │   └── main.css
    ├── api/
    │   ├── http.ts                 # axios 拦截器
    │   └── auth.ts                 # 登录/me
    ├── store/
    │   └── auth.ts                 # Pinia auth store
    ├── router/
    │   └── index.ts                # 路由 + 守卫
    └── views/
        ├── Login.vue               # 登录页
        ├── Layout.vue               # 登录后框架
        └── Dashboard.vue           # 工作台(显示后端健康 + 路线图)
```

## 6. 已实现页面

| 路径 | 说明 | 状态 |
|---|---|---|
| `/login` | 登录页 | ✅ M0 完成 |
| `/` | 工作台(Dashboard) | ✅ M0 完成 |
| `/projects` | 项目管理 | 🔒 M1 启用 |
| `/knowledge` | 知识库问答 | 🔒 M2 启用 |
| `/timepoints` | 时点日程 | 🔒 M3 启用 |
| `/rules` | 规则引擎 | 🔒 M4 启用 |

## 7. 下一阶段(M1)

- 项目管理页面(列表 / 详情 / 增删改)
- 议案 + 材料管理
- 文件上传组件(支持 docx/xlsx/pdf)
- 上传后即时显示解析进度

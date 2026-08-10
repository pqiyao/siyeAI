# 项目结构

四叶酒馆（Siye AI）由业务后端、运营后台、H5 用户端和 SillyTavern 运行时组成。仓库按可独立构建的模块划分，避免多余的版本目录和重复嵌套。

```text
siyeAI/
├── backend/       Spring Boot 业务后端
├── admin-web/     Vue 3 运营后台
├── h5-web/        uni-app H5 用户端
├── sillytavern/   SillyTavern 聊天运行时
├── docs/          项目文档与展示图片
├── LICENSE
└── README.md
```

## 模块职责

| 模块 | 技术栈 | 主要职责 |
| --- | --- | --- |
| `backend/` | Java 17、Spring Boot、MyBatis、Flyway | 鉴权、角色、会话、记忆、订单、权益、工单、运营接口和 AI 路由。 |
| `admin-web/` | Vue 3、Vite、Element Plus | 用户、角色、内容、订单、模型供应商和系统配置等运营功能。 |
| `h5-web/` | uni-app | 角色发现、详情、聊天、个人中心、支付、工单和移动端体验。 |
| `sillytavern/` | Node.js | 聊天运行时、模型连接、角色资源和扩展能力。 |
| `docs/` | Markdown、图片 | 项目说明、部署说明和界面预览。 |

## 调用关系

```text
H5 用户端 ─────┐
               ├──> Spring Boot 后端 ──> MySQL / Redis
运营后台 ──────┘             │
                             └──> SillyTavern / 模型供应商
```

`backend/` 是业务边界和统一 API 入口。前端不应直接持有模型、支付或数据库密钥；敏感配置应由后端通过环境变量读取。

## 开发入口

- 后端：`backend/pom.xml`
- 运营后台：`admin-web/package.json`
- H5 用户端：`h5-web/manifest.json`、`h5-web/pages.json`
- SillyTavern：`sillytavern/package.json`、`sillytavern/default/config.yaml`
- 数据库迁移：`backend/src/main/resources/db/`
- 后端主配置：`backend/src/main/resources/application.yaml`

模块内的构建产物、依赖目录、运行数据和本地密钥不应提交到仓库。

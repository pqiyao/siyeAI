# 部署说明

本文给出四叶酒馆的基础部署路径。生产环境应使用独立域名、HTTPS、强密码和受控的网络访问策略。

## 环境要求

- Java 17 和 Maven 3.9+
- Node.js 20+ 和 npm
- MySQL 8+
- Redis 6+
- Nginx 或其他反向代理
- HBuilderX，用于构建 uni-app H5

## 1. 准备依赖服务

创建独立的 MySQL 数据库和账号，并启动 Redis。数据库表由后端的 Flyway 迁移自动管理。不要在生产环境使用仓库中的示例密码或默认密钥。

至少配置以下环境变量：

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SPRING_DATA_REDIS_HOST
SPRING_DATA_REDIS_PORT
SPRING_DATA_REDIS_PASSWORD
APP_AUTH_SECRET
APP_RUOYI_ADMIN_PASSWORD
APP_RUOYI_JWT_SECRET
SILLYTAVERN_PUBLIC_BASE_URL
```

支付、邮件、模型、语音和图片生成功能需要按实际供应商继续配置对应环境变量。生产密钥不得写入源码、前端文件或 Git 历史。

## 2. 启动 SillyTavern

```bash
cd sillytavern
npm ci
npm start
```

默认端口为 `8000`。生产环境不要直接暴露该端口，应限制访问来源，并按需配置白名单和 API Key。

## 3. 构建并启动后端

```bash
cd backend
./mvnw clean package -DskipTests
java -jar target/*.jar
```

Windows 可使用 `.\mvnw.cmd`。后端默认监听 `8080`，首次启动会执行数据库迁移。建议通过进程管理器或容器托管，并设置健康检查。

## 4. 构建运营后台

```bash
cd admin-web
npm ci
npm run build:prod
```

构建结果位于 `admin-web/dist/`。使用 Nginx 托管静态文件，并将前端使用的 API 前缀反向代理到 Spring Boot 服务。生产构建通过 `VITE_APP_BASE_API` 设置 API 前缀；`VITE_BACKEND_URL` 仅用于本地 Vite 开发代理。不要把密钥写入任何 Vite 环境变量。

## 5. 构建 H5 用户端

使用 HBuilderX 打开 `h5-web/`，运行或发行到 H5。部署时应优先使用同源反向代理；如需独立 API 域名，应同步配置 HTTPS、CORS 和 WebSocket。

## 6. 反向代理建议

- 全站启用 HTTPS，并将 HTTP 重定向到 HTTPS。
- H5、后台和 API 使用明确的域名或路径边界。
- 不对公网暴露 MySQL、Redis、Actuator 管理端点和 SillyTavern 内部端口。
- 限制上传大小、请求速率和后台登录尝试次数。
- 对日志、上传目录和数据库做定期备份，并验证恢复流程。

## 上线检查

- 已替换所有默认密码、JWT/Auth secret 和管理员凭据。
- 已配置生产数据库、Redis、模型和支付密钥。
- 已关闭调试接口并限制管理后台访问。
- 已确认 CORS、回调地址、上传目录和文件权限。
- 已检查日志中不记录 Token、密码、支付信息和用户隐私数据。
- 已确认公开素材和第三方依赖符合各自许可证。

更复杂的高可用、灰度发布和集群部署不在本文范围内。

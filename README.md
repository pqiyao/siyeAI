# JiuGuanSJ / Siye AI Complete System

JiuGuanSJ is a full-stack AI character chat system. This open-source package contains the Spring Boot backend, the Vue admin console, the uni-app H5 client, deployment templates, and a bundled SillyTavern memory integration.

The repository is intended to be a clean public handoff: production secrets, private deployment notes, local databases, runtime uploads, build outputs, and local dependency folders should not be committed.

## Online Demo

The current public H5 client is available at [https://siyeai.pengqiyao.cn/](https://siyeai.pengqiyao.cn/).

This repository contains the complete open-source project. The live site may use production configuration that differs from the local Docker defaults. If this project is useful to you, a free Star is very welcome.

## Preview

| H5 discovery | AI chat |
| --- | --- |
| ![H5 discovery page](docs/images/h5-discovery.jpg) | ![AI chat page](docs/images/h5-chat.jpg) |

| Character library | Admin dashboard |
| --- | --- |
| ![Character library page](docs/images/h5-character-library.jpg) | ![Admin dashboard](docs/images/admin-dashboard.jpg) |

## Modules

| Path | Description |
| --- | --- |
| `backend/` | Spring Boot backend for auth, characters, chat, memory, social features, payment hooks, support tickets, admin APIs, uploads, and SillyTavern integration. |
| `admin-web/` | Vue 3 / Vite / Element Plus admin console based on the RuoYi Vue style. |
| `h5-web/` | uni-app H5 mobile client for discovery, character chat, profile, social, support, and user workflows. |
| `integrations/st-memory-enhancement/` | SillyTavern memory enhancement extension bundled as an optional integration reference. |
| `deploy/` | Docker Compose, nginx, and environment templates for local or server deployment. |
| `项目说明文档/` | Chinese project documentation covering architecture, quick start, configuration, backend, frontend, deployment, maintenance, and API mapping. |

## What You Can Run

The Docker Compose template can build and run MySQL, Redis, SillyTavern, the backend API, the admin web console, and the H5 web container.

Important deployment notes:

- The H5 image builds the uni-app H5 client during `docker compose up -d --build`.
- SillyTavern is included in Compose and is available to the backend at `http://sillytavern:8000`.
- Real secrets must be provided through `.env`; never commit production `.env` files.

## Quick Start With Docker Compose

```bash
cd deploy
cp .env.example .env
```

Edit `deploy/.env` and replace every placeholder value, especially:

```text
MYSQL_ROOT_PASSWORD
MYSQL_PASSWORD
APP_AUTH_SECRET
APP_RUOYI_ADMIN_PASSWORD
APP_RUOYI_JWT_SECRET
SILLYTAVERN_PUBLIC_BASE_URL
```

Start the full stack:

```bash
docker compose --env-file .env up -d --build
```

Default local ports:

| Service | URL |
| --- | --- |
| Backend API | `http://127.0.0.1:8080` |
| Admin web | `http://127.0.0.1:8081` |
| H5 web | `http://127.0.0.1:8082` |
| SillyTavern | `http://127.0.0.1:8000` |

For more details, see [deploy/README.md](deploy/README.md).

## Local Development

Backend:

```bash
cd backend
./mvnw spring-boot:run
```

Windows:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Admin web:

```bash
cd admin-web
npm install
npm run dev
```

H5 web:

```bash
cd h5-web
npm install
npm run dev:h5
```

Build output for H5 CLI builds is written to `h5-web/dist/build/h5`.

## Configuration

Start from the example environment files:

- Root example: `.env.example`
- Deploy example: `deploy/.env.example`
- Admin web examples: `admin-web/.env.development.example`, `admin-web/.env.production.example`, `admin-web/.env.staging.example`

The backend expects sensitive values through environment variables:

- `APP_AUTH_SECRET`
- `APP_RUOYI_ADMIN_PASSWORD`
- `APP_RUOYI_JWT_SECRET`
- `SPRING_DATASOURCE_PASSWORD`
- `SILLYTAVERN_API_KEY`, if your SillyTavern instance requires API authentication

Do not write production secrets into frontend code, committed YAML files, Dockerfiles, screenshots, issue examples, or documentation snippets.

## Documentation Map

Start here if you are new to the project:

1. [项目说明文档/01-项目概览.md](项目说明文档/01-项目概览.md)
2. [项目说明文档/02-快速启动.md](项目说明文档/02-快速启动.md)
3. [项目说明文档/03-配置说明.md](项目说明文档/03-配置说明.md)
4. [项目说明文档/04-后端说明.md](项目说明文档/04-后端说明.md)
5. [项目说明文档/05-前端说明.md](项目说明文档/05-前端说明.md)
6. [项目说明文档/06-部署说明.md](项目说明文档/06-部署说明.md)
7. [项目说明文档/07-开发维护.md](项目说明文档/07-开发维护.md)
8. [项目说明文档/08-接口地图.md](项目说明文档/08-接口地图.md)

## Release Hygiene

Before publishing or tagging a release, confirm:

- `node_modules/`, `target/`, `dist/`, `unpackage/`, local uploads, logs, databases, archives, and IDE files are not committed.
- `.env` files and production credentials are not committed.
- Demo images, audio, Live2D assets, and game assets are either original, properly licensed, or clearly replaceable.
- `LICENSE` uses the MIT License.
- `THIRD_PARTY_NOTICES.md` is kept up to date when bundled third-party material changes.

## License

This project is released under the MIT License. See [LICENSE](LICENSE) for details.

# Deployment Guide

This directory contains a public Docker Compose deployment template for JiuGuanSJ. It is designed for local evaluation, test servers, and as a starting point for production deployment.

The template intentionally avoids real domains, server IP addresses, private `.env` files, SSH commands, production archives, and internal incident notes.

## Files

| File | Purpose |
| --- | --- |
| `.env.example` | Environment variable template. Copy it to `.env` before running Compose. |
| `docker-compose.yml` | MySQL, Redis, SillyTavern, backend, admin web, and H5 web service definitions. |
| `st/config.yaml` | Generic SillyTavern container config. |
| `../h5-web/deploy/nginx.conf` | nginx config for serving the built H5 web client and proxying API/WebSocket traffic. |
| `README.md` | This deployment guide. |

## What Compose Starts

| Service | Source | Default Port | Description |
| --- | --- | --- | --- |
| `mysql` | `mysql:8.0` | internal only | Business database with `utf8mb4` defaults. |
| `redis` | `redis:7-alpine` | internal only | Cache, rate limit, concurrency, and runtime state support. |
| `sillytavern` | `ghcr.io/sillytavern/sillytavern:latest` | `8000` | SillyTavern service used by backend integration. |
| `backend` | Builds from `../backend` | `8080` | Spring Boot API service. |
| `admin-web` | Builds from `../admin-web` | `8081` | Vue admin console served by nginx. |
| `h5-web` | Builds from `../h5-web` | `8082` | Builds uni-app H5 and serves it with nginx. |

## Deployment Notes

This Compose stack is intended to be complete for local evaluation and server bootstrap:

- H5 is built inside the `h5-web` image with `npm run build:h5`.
- SillyTavern is started as the `sillytavern` service.
- The backend connects to SillyTavern through the internal Docker URL `http://sillytavern:8000`.
- Real secrets still belong in `.env`, not in committed files.

## First Run

Copy the environment template:

```bash
cd deploy
cp .env.example .env
```

Edit `.env` and replace all placeholders. At minimum, review:

```text
MYSQL_ROOT_PASSWORD
MYSQL_PASSWORD
APP_AUTH_SECRET
APP_RUOYI_ADMIN_PASSWORD
APP_RUOYI_JWT_SECRET
APP_CORS_ALLOWED_ORIGIN_PATTERNS
SILLYTAVERN_PUBLIC_BASE_URL
SILLYTAVERN_API_KEY
```

Start services:

```bash
docker compose --env-file .env up -d --build
```

Check status:

```bash
docker compose ps
docker compose logs -f backend
```

Open local services:

| Service | URL |
| --- | --- |
| Backend health | `http://127.0.0.1:8080/actuator/health` |
| Admin web | `http://127.0.0.1:8081` |
| H5 web | `http://127.0.0.1:8082` |
| SillyTavern | `http://127.0.0.1:8000` |

## Updating

Pull or copy the latest source, then rebuild:

```bash
cd deploy
docker compose --env-file .env up -d --build
```

Flyway migrations run automatically when the backend starts. Back up the database before applying migrations in production.

## Persistent Volumes

| Volume | Stores |
| --- | --- |
| `mysql-data` | MySQL database files. |
| `redis-data` | Redis append-only persistence. |
| `uploads` | Backend-uploaded files. |
| `st-asset-cache` | Cached SillyTavern assets proxied by the backend. |
| `st-config` | SillyTavern config files. |
| `st-data` | SillyTavern data directory. |
| `st-plugins` | SillyTavern plugins. |
| `st-extensions` | SillyTavern third-party extensions. |

Production deployments should define backup and restore procedures for these volumes.

## Reverse Proxy Notes

For production, place nginx, Caddy, Traefik, or a cloud load balancer in front of the Compose stack.

Recommended rules:

- Serve H5 and API under the same domain when possible to reduce CORS complexity.
- Preserve WebSocket headers: `Upgrade` and `Connection`.
- Disable proxy buffering or increase timeouts for SSE chat endpoints.
- Increase request body limits for upload endpoints.
- Restrict admin routes to trusted networks when possible.
- Do not expose MySQL or Redis directly to the public internet.

## Security Checklist

Before exposing the stack publicly:

- `.env` is not committed.
- All `change-this-*` values are replaced.
- Admin password is a bcrypt hash and is not reused elsewhere.
- `APP_AUTH_SECRET` and `APP_RUOYI_JWT_SECRET` are long random values.
- CORS and WebSocket origins only allow trusted domains.
- Mock payment is not enabled in production unless explicitly intended.
- Payment keys, Telegram tokens, and SillyTavern API keys are injected securely.
- Database, Redis, uploads, and logs have a backup and retention policy.

# Admin Web

`admin-web/` is the Vue 3 / Vite / Element Plus admin console for JiuGuanSJ. It is based on the RuoYi Vue style and adapted for the AI character chat backend.

## Main Features

- Dashboard and operational statistics.
- H5 user management.
- Character, tag, lorebook, and review management.
- Model provider, routing, and AI log management.
- Store products, orders, payment channels, and entitlement management.
- Support tickets and reports.
- Community posts, comments, and settings.
- Human chat conversation and message moderation.
- Illustration works, notices, and access keys.
- Admin accounts, roles, and permission change logs.

## Development

Install dependencies:

```bash
npm install
```

Start dev server:

```bash
npm run dev
```

Build production assets:

```bash
npm run build:prod
```

Preview production build:

```bash
npm run preview
```

## Configuration

Use the example files as templates:

```text
.env.development.example
.env.production.example
.env.staging.example
```

Common variables:

| Variable | Description |
| --- | --- |
| `VITE_APP_TITLE` | Admin page title. |
| `VITE_APP_ENV` | Build environment label. |
| `VITE_APP_BASE_API` | API prefix used by request helpers. |
| `VITE_BACKEND_URL` | Backend target for local dev proxy. |
| `VITE_DEV_PORT` | Vite dev server port. |

Initial admin credentials are controlled by backend environment variables:

```text
APP_RUOYI_ADMIN_USERNAME
APP_RUOYI_ADMIN_PASSWORD
APP_RUOYI_JWT_SECRET
```

## Security

Do not publish real admin passwords, JWT secrets, API keys, production domains, or `.env` files. Use `.env.*.example` files for placeholders only.

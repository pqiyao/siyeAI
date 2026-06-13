# Contributing

Thanks for helping improve JiuGuanSJ. This project touches backend services, web clients, deployment, AI integrations, and user-generated content flows, so focused and well-tested changes are especially valuable.

## Good First Areas

- Documentation improvements
- Local setup and Docker fixes
- UI compatibility fixes
- Security hardening
- Bug reports with clear reproduction steps
- Test coverage for existing behavior

## Pull Request Rules

1. Keep each pull request focused on one topic.
2. Explain the motivation, user impact, and testing performed.
3. Include screenshots or screen recordings for visible frontend changes.
4. Add or update tests when backend behavior changes.
5. Update `.env.example` and documentation when adding configuration.
6. Do not commit generated builds, dependency folders, logs, uploads, databases, archives, or local IDE files.
7. Do not commit secrets, private certificates, keystores, payment keys, API keys, tokens, or production `.env` files.

## Development Notes

- Backend code lives in `backend/` and uses Spring Boot, MyBatis, Flyway, MySQL, and Redis.
- Admin code lives in `admin-web/` and uses Vue 3, Vite, Element Plus, and Pinia.
- H5 code lives in `h5-web/` and is built with the uni-app toolchain.
- Deployment templates live in `deploy/`.

## Safety Boundaries

Do not contribute features designed for illegal activity, credential theft, impersonation, fraud, harassment, copyright infringement, or malicious content generation.

If a change affects authentication, payments, uploads, WebSocket, user-generated content, or model-provider credentials, call that out clearly in the pull request.

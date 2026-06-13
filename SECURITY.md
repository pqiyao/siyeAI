# Security Policy

## Reporting Security Issues

Please do not publish exploit details in public issues. Report security problems through the maintainer contact channel listed in the project documentation or repository profile.

Include:

- Affected module or endpoint
- Reproduction steps
- Expected impact
- Whether credentials, user data, payment data, uploads, or model-provider keys may be affected

## Secrets Policy

This repository must not contain:

- Production `.env` files
- API keys or model-provider keys
- Private certificates, keystores, or SSH keys
- Payment private keys
- Telegram bot tokens or webhook secrets
- Database dumps or real user data
- Runtime uploads, logs, generated builds, or deployment archives

Use `.env.example` files for placeholders only.

## Release Security Checklist

Before publishing a release:

1. Run a secret scan.
2. Confirm `.env` files are ignored and uncommitted.
3. Confirm build outputs and dependency folders are not committed.
4. Confirm production credentials were rotated if they were ever committed.
5. Confirm bundled assets are safe to redistribute.
6. Confirm CORS, WebSocket origins, admin access, uploads, and payment integrations are documented for production hardening.

## Deployment Hardening

For production:

- Use long random values for `APP_AUTH_SECRET` and `APP_RUOYI_JWT_SECRET`.
- Store admin passwords as bcrypt hashes.
- Do not expose MySQL or Redis to the public internet.
- Restrict admin access where possible.
- Allow only trusted CORS and WebSocket origins.
- Keep payment and Telegram secrets outside source control.
- Back up database and upload volumes.

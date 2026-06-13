# Third-Party Notices

This repository contains original application code and selected third-party components or integrations. Review upstream licenses before public release or commercial redistribution.

## Major Third-Party Components

| Component | Location | Notes |
| --- | --- | --- |
| RuoYi Vue3 style admin frontend | `admin-web/` | Admin console is based on the RuoYi Vue ecosystem and adapted for this project. Review upstream MIT license notices. |
| SillyTavern integration material | `integrations/st-memory-enhancement/` | Bundled as an optional integration reference. Review SillyTavern and extension licensing before redistribution. |
| uni-app ecosystem | `h5-web/` | Includes uni-app project structure and uni modules. Review each module's upstream license. |
| UI libraries | `admin-web/package.json`, `h5-web/package.json` | Element Plus, uView, FirstUI, ColorUI, Vue, Vite, Pinia, and related packages. |
| Backend libraries | `backend/pom.xml` | Spring Boot, MyBatis, Flyway, MySQL connector, Redis client, JWT libraries, and test dependencies. |
| Game/demo/media assets | `h5-web/static/`, `backend/src/main/resources/static/` | Verify each image, audio, Live2D model, SVG, and demo asset before public release. |

## Asset Review Requirement

Before publishing a public release, confirm every bundled image, audio file, Live2D model, demo artwork, game asset, icon, and font is one of:

- Original project-owned material
- Licensed for redistribution
- Clearly marked as a replaceable placeholder
- Removed from the public package

## Dependency Manifests

For complete package-level dependency information, inspect:

- `backend/pom.xml`
- `admin-web/package.json`
- `admin-web/package-lock.json`
- `h5-web/package.json`
- `h5-web/package-lock.json`, if present
- `h5-web/uni_modules/**/package.json`

# 第三方组件与素材说明

这份文档用于说明四叶酒馆 / Siye AI / JiuGuanSJ 开源版本中涉及的主要第三方组件，以及公开发布时需要注意的素材边界。

## 主要第三方组件

| 类别 | 位置 | 说明 |
| --- | --- | --- |
| Spring Boot / MyBatis / Flyway | `backend/` | 后端框架、数据访问与数据库迁移能力。 |
| Vue 3 / Vite / Element Plus | `admin-web/` | 运营后台技术栈。 |
| RuoYi Vue 风格后台 | `admin-web/` | 后台管理端基于 RuoYi Vue 风格改造。 |
| uni-app 生态 | `h5-web/` | H5 用户端工程结构与构建体系。 |
| SillyTavern 集成 | `integrations/`、`deploy/` | 用于角色上下文、世界书和聊天运行时集成参考。 |
| Docker / Nginx | `deploy/` | 私有化部署、反向代理与静态资源服务。 |

## 依赖清单入口

完整依赖请以仓库中的清单文件为准：

- `backend/pom.xml`
- `admin-web/package.json`
- `admin-web/package-lock.json`
- `h5-web/package.json`
- `h5-web/package-lock.json`
- `h5-web/uni_modules/**/package.json`

## 素材边界

开源版本中的图片、插画、音频、字体、Live2D 模型和其他视觉素材，公开前应满足以下任一条件：

- 项目原创
- 已获得可再分发授权
- 明确作为占位素材使用
- 已从公开仓库中移除

用户上传内容、来源不明素材、仅限商业私用但不可再分发的资源，不应进入开源仓库。

## 说明

本文件用于帮助理解项目的第三方来源与公开边界，但不替代正式许可证审查。二次发布、商业使用或再分发前，请自行检查相关依赖、上游项目和素材资源的许可证要求。

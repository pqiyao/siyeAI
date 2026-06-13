# 第三方组件与素材说明

这份文档用于说明四叶酒馆 / Siye AI / JiuGuanSJ 开源版本中涉及的第三方依赖、上游项目、集成组件和素材审查原则。

它不是完整许可证清单的替代品。真正发布、二次分发或商业使用前，仍需要检查各依赖包、上游项目和素材文件自己的许可证。

## 主要第三方组件

| 类别 | 位置 | 说明 |
| --- | --- | --- |
| Spring Boot 生态 | `backend/pom.xml` | 后端基础框架，包含 Web、配置、测试等能力。 |
| MyBatis / MyBatis Plus 相关 | `backend/pom.xml` | 数据访问、Mapper、分页和业务表操作。 |
| Flyway | `backend/src/main/resources/db/migration/` | 数据库版本迁移。 |
| MySQL Connector | `backend/pom.xml` | MySQL 数据库连接。 |
| Redis 客户端 | `backend/pom.xml` | 缓存、会话或运行时状态相关能力。 |
| JWT / 安全相关库 | `backend/pom.xml` | 鉴权、Token 和安全能力。 |
| Vue 3 / Vite / Pinia | `admin-web/package.json` | 后台管理端基础技术栈。 |
| Element Plus | `admin-web/package.json` | 后台管理端 UI 组件库。 |
| RuoYi Vue 风格后台 | `admin-web/` | 后台管理端基于 RuoYi Vue 生态风格改造，需保留并遵守上游许可证要求。 |
| uni-app 生态 | `h5-web/` | H5 用户端基础工程和构建体系。 |
| uView / FirstUI / ColorUI 等 | `h5-web/uni_modules/`、`h5-web/package.json` | H5 用户端 UI 与组件能力，需逐项检查模块许可证。 |
| SillyTavern 集成 | `integrations/st-memory-enhancement/`、`deploy/` | 作为聊天运行时和记忆增强集成参考，使用前需遵守 SillyTavern 及相关扩展许可证。 |
| Docker / Nginx | `deploy/` | 本地和服务器部署编排、反向代理、静态资源服务。 |

## 依赖清单入口

完整依赖请以项目内清单文件为准：

- `backend/pom.xml`
- `admin-web/package.json`
- `admin-web/package-lock.json`
- `h5-web/package.json`
- `h5-web/package-lock.json`
- `h5-web/uni_modules/**/package.json`

建议在正式发布前运行依赖许可证检查工具，并把新增依赖同步记录在本文件中。

## 素材资产审查

AI 角色互动项目通常会包含头像、封面、插画、音频、字体、Live2D 模型、游戏素材和演示图片。它们比代码更容易产生授权风险。

公开发布前，每一项素材都应属于以下情况之一：

| 状态 | 是否适合公开 | 说明 |
| --- | --- | --- |
| 项目原创 | 适合 | 作者自己创作或项目拥有完整授权。 |
| 明确可再分发 | 适合 | 许可证允许随项目分发，并按要求署名。 |
| 占位素材 | 适合 | 明确标记为 placeholder，只用于演示结构。 |
| 用户上传或生产数据 | 不适合 | 不应进入开源仓库。 |
| 来源不明素材 | 不适合 | 在确认授权前不要公开。 |
| 商业授权但不可再分发 | 不适合 | 即使作者可用，也不代表可以放进开源仓库。 |

## 本开源包的素材处理原则

当前开源包应遵循：

- 文档截图用于展示项目形态。
- QQ 群图片用于社区入口展示。
- 真实生产密钥、用户数据、数据库文件、上传目录不进入仓库。
- 大体积真实艺术资源和未确认授权素材不进入仓库。
- 需要保留目录结构时，使用 `README.md` 或 `placeholder.svg` 说明替换方式。

## SillyTavern 与扩展说明

本项目包含 SillyTavern 相关集成材料，用于说明角色上下文、世界书、记忆增强和聊天运行时如何接入业务系统。

请注意：

- SillyTavern 本体和相关扩展有自己的许可证与社区规则。
- 本仓库中的集成材料不等于重新授权 SillyTavern。
- 如果你要把相关组件一起再分发，请先检查上游仓库许可证、NOTICE、署名要求和商用限制。

## RuoYi / 后台生态说明

后台管理端基于 RuoYi Vue 生态风格进行项目化改造。使用、修改或再分发时，应保留上游项目所要求的许可证信息和署名信息。

如果后续继续改造后台模板、权限系统、菜单系统或代码生成相关逻辑，请同步检查上游许可证要求。

## 发布前许可证检查清单

发布公开版本前建议逐项确认：

1. `backend/pom.xml` 新增依赖是否允许开源分发。
2. `admin-web/package.json` 和 `h5-web/package.json` 新增依赖是否允许开源分发。
3. `h5-web/uni_modules/` 中模块是否有明确许可证。
4. `static/`、`public/`、`resources/static/` 中素材是否原创、已授权或占位。
5. README 截图、演示图、群图片是否可公开。
6. 没有把生产数据库、用户上传、聊天记录、支付记录或私有配置打包进仓库。
7. 新增第三方项目或模板后，已在本文档记录。

## 免责声明

本文件尽力说明项目中主要第三方来源和审查原则，但不能替代法律意见。使用者在二次发布、商业部署或再分发前，应自行完成许可证审查和合规判断。

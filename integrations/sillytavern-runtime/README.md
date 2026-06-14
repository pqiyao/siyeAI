# SillyTavern Runtime 集成

本目录保存四叶酒馆部署时使用的 SillyTavern runtime 关键改动。

当前同步文件：

- `src/endpoints/runtime-chat.js`

这份文件对应部署环境中的：

```text
SillyTavern-release/SillyTavern-release/src/endpoints/runtime-chat.js
```

## 本次续写修复

聊天页“续写”会在上下文末尾追加一条继续生成的提示。旧实现里这条提示容易以最后一条 `system` 消息进入模型请求，部分自定义模型或第三方厂商会直接返回空内容并停止。

现在 runtime 默认使用 `user` 角色发送续写提示，并保留可配置入口：

- `continueNudgeRole`
- `continue_nudge_role`
- `ST_RUNTIME_CONTINUE_NUDGE_ROLE`
- settings 中的 `continue_nudge_role`

默认提示内容仍为：

```text
[Continue your last message without repeating its original content.]
```

如遇到特殊模型，也可以通过配置把角色调整为 `system`、`assistant` 或 `developer`。

## 维护建议

如果后续升级 SillyTavern 上游版本，请重点对比并重新合并：

- 续写提示追加逻辑
- `continue_nudge_role` 配置读取
- runtime generate 的 `mode === 'continue'` 分支


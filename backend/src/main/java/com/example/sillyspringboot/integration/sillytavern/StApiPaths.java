package com.example.sillyspringboot.integration.sillytavern;

/**
 * SillyTavern HTTP 路径唯一集中地；与本地 ST 源码路由一致，禁止在业务包复制字面量。
 */
public final class StApiPaths {

    private StApiPaths() {}

    public static final String CHAT_COMPLETIONS_STATUS = "/api/backends/chat-completions/status";
    public static final String CHAT_COMPLETIONS_GENERATE = "/api/backends/chat-completions/generate";

    /** 返回的 {@code settings} 字段为整站 settings.json 的 JSON 字符串（含 chat_completion_source、各 * _model 等）。 */
    public static final String SETTINGS_GET = "/api/settings/get";
    public static final String SETTINGS_SAVE = "/api/settings/save";

    public static final String CHARACTERS_ALL = "/api/characters/all";
    public static final String CHARACTERS_GET = "/api/characters/get";
    public static final String CHARACTERS_CHATS = "/api/characters/chats";
    public static final String CHARACTERS_CREATE = "/api/characters/create";
    public static final String CHARACTERS_EDIT = "/api/characters/edit";
    public static final String CHARACTERS_IMPORT = "/api/characters/import";
    public static final String CHARACTERS_DELETE = "/api/characters/delete";

    public static final String CHATS_SAVE = "/api/chats/save";
    public static final String CHATS_GET = "/api/chats/get";
    public static final String CHATS_RECENT = "/api/chats/recent";
    public static final String CHATS_DELETE = "/api/chats/delete";

    public static final String WORLDINFO_LIST = "/api/worldinfo/list";
    public static final String WORLDINFO_GET = "/api/worldinfo/get";
    public static final String WORLDINFO_EDIT = "/api/worldinfo/edit";
    public static final String WORLDINFO_DELETE = "/api/worldinfo/delete";

    /** 方案一（StepA）：ST chat 运行时写入与同源 messages 构建 */
    public static final String RUNTIME_CHAT_APPEND = "/api/runtime/chat/append";
    public static final String RUNTIME_CHAT_BUILD = "/api/runtime/chat/build";
    public static final String RUNTIME_CHAT_GENERATE = "/api/runtime/chat/generate";
    public static final String RUNTIME_CHAT_STOP = "/api/runtime/chat/stop";
    public static final String RUNTIME_CHAT_POP_LAST_ASSISTANT = "/api/runtime/chat/pop-last-assistant";
    public static final String RUNTIME_CHAT_REPLACE_LAST_ASSISTANT = "/api/runtime/chat/replace-last-assistant";
    public static final String RUNTIME_CHAT_TAIL = "/api/runtime/chat/tail";
    public static final String RUNTIME_CHAT_GOLDEN_CASE_SAVE = "/api/runtime/chat/golden-case/save";
    public static final String RUNTIME_CHAT_GOLDEN_CASE_LIST = "/api/runtime/chat/golden-case/list";
    public static final String RUNTIME_CHAT_GOLDEN_CASE_RUN = "/api/runtime/chat/golden-case/run";

    public static final String VECTOR_QUERY = "/api/vector/query";
    public static final String VECTOR_INSERT = "/api/vector/insert";

    public static final String SD_COMFY_WORKFLOW = "/api/sd/comfy/workflow";
    public static final String SD_COMFY_GENERATE = "/api/sd/comfy/generate";
}

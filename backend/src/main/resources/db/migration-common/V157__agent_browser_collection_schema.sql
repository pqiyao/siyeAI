UPDATE app_agent_tool_definition
SET input_schema = '{"type":"object","properties":{"url":{"type":"string","maxLength":2048},"urls":{"type":"array","minItems":1,"maxItems":3,"items":{"type":"string","maxLength":2048}}},"additionalProperties":false}',
    description = '读取配置白名单公网网页的标题、纯文本摘要和链接；支持一次最多 3 个白名单页面集合；禁止重定向、脚本执行、点击、表单提交和内网地址。',
    updated_at = CURRENT_TIMESTAMP
WHERE tool_key = 'browser.open';

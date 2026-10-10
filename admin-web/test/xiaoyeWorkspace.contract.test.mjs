import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const source = readFileSync(resolve('src/views/jiugai/agent/chat/index.vue'), 'utf8')
const apiSource = readFileSync(resolve('src/api/jiugai/xiaoye.js'), 'utf8')
const companionSource = readFileSync(resolve('src/views/jiugai/agent/chat/XiaoyeCompanion.vue'), 'utf8')
const polishSource = readFileSync(resolve('src/views/jiugai/agent/chat/workbench-polish.css'), 'utf8')
const backendSource = readFileSync(resolve('../../SillySpringboot/src/main/java/com/example/sillyspringboot/agent/service/DeepSeekHarnessService.java'), 'utf8')
const controllerSource = readFileSync(resolve('../../SillySpringboot/src/main/java/com/example/sillyspringboot/admin/web/AdminXiaoyeController.java'), 'utf8')

test('小叶工作台读取 DSH 能力快照并展示能力中心', () => {
  for (const field of ['dshModels', 'dshProviders']) assert.match(source, new RegExp(`data\\.${field}`))
  assert.match(source, /listXiaoyeConversationSkills/)
  assert.match(source, /listXiaoyeConversationSubagents/)
  assert.match(source, /conversationId\.value/)
  assert.match(source, /rowsFromDsh\(subagents\.value\)/)
  assert.match(source, /小叶能力中心/)
  assert.match(source, /loadDshCapabilities/)
})

test('小叶工作台正确解包 DSH result.value.events 并只展示最终消息', () => {
  assert.match(source, /result\?\.value/)
  assert.match(source, /assistant\/message/)
  assert.match(source, /user\/message/)
  assert.match(source, /content\s*\.filter\(\s*\(?\s*block\s*\)?\s*=>\s*block\?\.type\s*===\s*['"]text['"]/)
  assert.doesNotMatch(source, /r\.data\?\.items\|\|r\.data\?\.events\|\|r\.data\?\.messages/)
})

test('小叶工作台隐藏注入到 DSH 历史中的内部身份和模式提示词', () => {
  assert.match(source, /function cleanHistoryText\(value\)/)
  assert.match(source, /xiaoye-mode:\[\^>\]\+/)
  assert.match(source, /以上是本轮必须遵守的小叶身份与工作约束/)
  assert.match(source, /用户接下来的内容是实际请求/)
})

test('小叶工作台进入页面会恢复最近会话并显式展示历史加载失败', () => {
  assert.match(source, /loadConversations\(\)/)
  assert.match(source, /localStorage\.getItem\(conversationStorageKey\(\)\)/)
  assert.match(source, /sort\(\s*\(\s*a\s*,\s*b\s*\)\s*=>\s*b\.updatedAt\s*-\s*a\.updatedAt\s*\)/)
  assert.match(source, /historyError/)
  assert.match(source, /reloadCurrentConversation/)
  assert.match(source, /openConversation\(\s*selected\s*,\s*\{\s*persist\s*:\s*false\s*\}\s*\)/)
  assert.match(source, /实时历史连接失败，请重试加载/)
})

test('小叶功能模式进入发送请求并对应后端模式契约', () => {
  for (const mode of ['CHAT', 'CHARACTER_CREATION', 'PLATFORM_QUERY', 'TASK_EXECUTION', 'ENGINEERING']) {
    assert.match(source, new RegExp(mode))
  }
  assert.match(source, /sendXiaoyeMessage\(\s*id\s*,\s*value\s*,\s*modeContract\.wireMode\s*\)/)
  assert.match(source, /wireMode/)
})

test('真实任务进入执行面板并在后端终态停止刷新', () => {
  assert.match(source, /mode\.value === ["']task["'] \|\| mode\.value === ["']engineering["']/)
  assert.match(source, /responseData\.taskCreated\s*&&\s*responseData\.task\?\.id/)
  assert.match(source, /startTaskRefresh\(\)/)
  assert.match(source, /refreshSelectedTask/)
  assert.match(source, /getXiaoyeTask\(taskId\)/)
  assert.match(source, /window\.setInterval/)
  assert.match(source, /SUCCEEDED.*PARTIAL_SUCCESS.*COMPLETED.*FAILED.*CANCELLED.*REJECTED/s)
  assert.match(source, /stopTaskRefresh\(\)/)
  assert.match(source, /onBeforeUnmount[\s\S]*stopTaskRefresh\(\)/)
})

test('任务状态使用完整后端状态表且任务详情从 data 中读取步骤', () => {
  assert.match(source, /function displayStatus\(status\)/)
  for (const status of ['WAITING_APPROVAL', 'RUNNING', 'SUCCEEDED', 'PARTIAL_SUCCESS', 'FAILED']) {
    assert.match(source, new RegExp(`${status}:`))
  }
  assert.match(source, /const detail\s*=\s*r\?\.data\s*\|\|\s*r\s*\|\|\s*\{\}/)
  assert.match(source, /steps:\s*detail\.steps\s*\|\|\s*\[\]/)
})

test('DSH 活动将开始和结束事件折叠到同一身份行', () => {
  assert.match(source, /latestTurnStartIndex[\s\S]*rows\.slice\(latestTurnStartIndex\)/s)
  assert.match(source, /const itemById\s*=\s*new Map\(\)/)
  assert.match(source, /const finishKey\s*=\s*\(kind, explicitKey, turnPrefix\)/)
  assert.match(source, /type === "turn\/start"[\s\S]*upsert\(activeTurnKey/s)
  assert.match(source, /type === "turn\/end"[\s\S]*upsert\(key/s)
  assert.match(source, /type === "step\/start" \|\| type === "step\/end"/)
  assert.doesNotMatch(source, /id:\s*`step-end-\$\{seq\}`/)
  assert.doesNotMatch(source, /id:\s*`turn-end-\$\{seq\}`/)
})

test('真实任务清理旧会话活动并立即展示可操作审批', () => {
  assert.match(source, /responseData\.taskCreated[\s\S]*stopEventStream\(\)[\s\S]*activity\.value\s*=\s*\[\]/s)
  assert.match(source, /steps:\s*responseData\.steps\s*\|\|\s*responseData\.task\.steps\s*\|\|\s*\[\]/)
  assert.match(source, /responseData\.approval\?\.id/)
  assert.match(source, /selectedTaskApprovals/)
  assert.match(source, /@click="approve\(row\)"/)
  assert.match(source, /@click="reject\(row\)"/)
})

test('非任务问法继续等待模型回答且不会伪造执行面板', () => {
  assert.match(source, /if \(responseData\.taskCreated && responseData\.task\?\.id\)/)
  assert.match(source, /await loadConversationModels\(\);\s*startEventStream\(\);[\s\S]*await waitForReply\(previousCount\)/s)
  assert.doesNotMatch(source, /else if \(value === "task" \|\| value === "engineering"\) tab\.value = "execution"/)
})

test('审批后仅刷新任务和审批数据，不重新加载会话', () => {
  assert.match(source, /function refreshOperationalData\(\)/)
  assert.match(source, /listAgentTasks\(taskParams\)/)
  assert.match(source, /listAgentApprovals\(approvalParams\)/)
  const approvalHandlers = source.slice(source.indexOf('async function approve(row)'), source.indexOf('async function saveCard()'))
  assert.doesNotMatch(approvalHandlers, /await load\(\)/)
})

test('后端任务与工程请求在创建平台任务后直接返回，不再双重提示 DSH', () => {
  assert.match(controllerSource, /TASK_EXECUTION.*ENGINEERING[\s\S]*taskService\.createAdminTask/s)
  assert.match(controllerSource, /data\.put\("message"[\s\S]*return AdminAjaxResult\.okData\(data\);[\s\S]*session\.prompt/s)
})

test('平台查询结果交给模型总结而不是由前端逐行拼接回答', () => {
  assert.match(source, /payload\.question\s*=\s*value/)
  assert.match(source, /小叶正在分析平台数据/)
  assert.match(source, /waitForReply\(previousCount\)/)
  assert.match(source, /xiaoye-platform-context/)
  assert.match(controllerSource, /queuePlatformSummary\(conversationId, question, data\)/)
  assert.match(controllerSource, /harness\.writeRpc\("session\.prompt", payload\)/)
  assert.match(controllerSource, /不要逐行照抄/)
})

test('平台授权同时验证管理员登录和小叶服务通道', () => {
  assert.match(source, /getXiaoyePlatformAuthorization/)
  assert.match(source, /Promise\.all\(\[\s*getXiaoyeBootstrap\(\),\s*getXiaoyePlatformAuthorization\(\)/s)
  assert.match(source, /serviceCredentialConfigured/)
  assert.match(source, /serviceAccountReady/)
  assert.match(source, /fullPlatformAccess/)
  assert.match(source, /只有两条链路都通过/)
})

test('平台查询结果支持展开和收起且新结果自动展开', () => {
  assert.match(source, /platformResultCollapsed/)
  assert.match(source, /展开查询结果/)
  assert.match(source, /收起查询结果/)
  assert.match(source, /platformResult\.value\s*=\s*result\?\.data\s*\|\|\s*result\s*\|\|\s*\{\};\s*platformResultCollapsed\.value\s*=\s*false/s)
})

test('平台能力问法必须查询真实能力目录而不是让通用模型猜测', () => {
  for (const phrase of ['有什么功能', '有哪些功能', '能做什么', '平台数据接口', '查数据库', '查询数据库']) {
    assert.match(source, new RegExp(`text\\.includes\\(\\"${phrase}\\"\\)`))
  }
  assert.match(source, /payload\.resource\s*=\s*"platform_capabilities"/)
})

test('普通对话中的明确平台数据问题会自动调用真实查询接口', () => {
  assert.match(source, /function shouldAutoRoutePlatform\(value, payload, metric\)/)
  assert.match(source, /查\|查看\|查询\|统计\|多少\|数量\|总数\|列表/)
  assert.match(source, /mode\.value === "chat"\s*&&\s*shouldAutoRoutePlatform\(value, detectedPlatformPayload, detectedMetric\)/)
  assert.match(source, /if \(mode\.value === "data" \|\| autoRoutePlatform\)/)
  assert.match(source, /if \(autoRoutePlatform\) mode\.value = "data"/)
})

test('AI 生成统计直接进入参数化指标接口且不要求用户名', () => {
  assert.match(source, /function parsePlatformMetric\(value\)/)
  for (const metric of ['ai_generation_daily', 'ai_generation_summary', 'order_summary', 'revenue_summary', 'dashboard_overview']) {
    assert.match(source, new RegExp(metric))
  }
  assert.match(source, /metric\.metric === "user_daily_chat_count"/)
  assert.match(source, /queryXiaoyePlatformMetric\(id, metric\)/)
})

test('任务消息携带单次请求编号供后端幂等复用', () => {
  assert.match(apiSource, /const turnRequestId\s*=\s*\(\)\s*=>/)
  assert.match(apiSource, /data:\s*\{\s*message,\s*mode,\s*requestId:\s*turnRequestId\(\)\s*\}/)
  assert.match(controllerSource, /taskBody\.put\("idempotencyKey", conversationId \+ ":" \+ requestId\)/)
  assert.match(controllerSource, /planService\.generateIfNeeded\(taskId, ownerAdminId\)/)
})

test('无法识别的平台问题查询真实能力目录且不再让通用模型猜测', () => {
  assert.match(source, /if \(text\.includes\("任务"\)\) return "tasks";\s*return "";/)
  assert.match(source, /if \(!payload\.resource\) payload\.resource = "platform_capabilities"/)
  assert.doesNotMatch(source, /if \(!payload\.resource\) \{\s*await sendXiaoyeMessage\(id, value, modeContract\.wireMode\)/)
})

test('小叶工作台处理 DSH ask_user_question 的请求与回答闭环', () => {
  assert.match(source, /respondXiaoyeQuestion/)
  assert.match(source, /questions\/stream/)
  assert.match(source, /question\/requested|requested/)
  assert.match(source, /questionAnswers/)
  assert.match(source, /submitUserQuestions/)
  assert.match(source, /cancelUserQuestions/)
  assert.match(source, /rpcId/)
  assert.match(source, /等待你的回答/)
  assert.match(source, /value\.kind\s*===\s*['"]completed['"]\s*\)/)
})

test('Spring Boot 通过 DSH mux 和 /api/respond 桥接用户问题', () => {
  assert.match(backendSource, /api\/events\.mux/)
  assert.match(backendSource, /WebSocket\.Listener/)
  assert.match(backendSource, /replaceFirst\("\^http", "ws"\)/)
  assert.match(backendSource, /onOpen\.run\(\)/)
  assert.match(backendSource, /client-response/)
  assert.match(backendSource, /question\/requested/)
  assert.match(backendSource, /respondUserQuestion/)
  assert.match(controllerSource, /questions\/stream/)
  assert.match(controllerSource, /questions\/{rpcId}\/respond/)
})

test('发送模型请求前等待真实 DSH 问题通道就绪', () => {
  assert.match(source, /function ensureQuestionStreamReady\(\)/)
  assert.match(source, /const id = await ensure\(\);\s*await ensureQuestionStreamReady\(\);/s)
  assert.match(controllerSource, /streamUserQuestions[\s\S]*status", "connected"/s)
})

test('实时会话与问答通道使用环境 API 前缀而不是写死开发地址', () => {
  assert.match(source, /import\.meta\.env\.VITE_APP_BASE_API/)
  assert.match(source, /function apiPath\(path\)/)
  assert.match(source, /apiPath\([\s\S]*questions\/stream/s)
  assert.doesNotMatch(source, /\/dev-api\/admin\/jiugai\/xiaoye/)
})

test('小叶工作台同时桥接 DSH 原生审批请求，避免审批工具停在执行中', () => {
  assert.match(source, /respondXiaoyeApproval/)
  assert.match(source, /approval-requested/)
  assert.match(source, /submitApproval/)
  assert.match(backendSource, /approval\/requested/)
  assert.match(backendSource, /respondApproval/)
  assert.match(controllerSource, /approvals\/{rpcId}\/respond/)
})

test('问题和审批完成事件使用 DSH 的真实关联键清理挂起状态', () => {
  assert.match(backendSource, /questionRpcId/)
  assert.match(backendSource, /approvalId\.equals\(entry\.getValue\(\)\.approvalId\(\)\)/)
  assert.match(source, /frame\?\.questionRpcId\s*\|\|\s*frame\?\.rpcId/)
  assert.match(source, /item\.approvalId\s*===\s*approvalId/)
})

test('小叶工作台通过统一任务接口发起受控 DSH 任务', () => {
  assert.match(source, /createAgentTask/)
  assert.match(source, /DSH_BROWSER/)
  assert.match(source, /DSH_DEVOPS_RELEASE/)
  assert.match(source, /DSH_PLATFORM_AUDIT/)
  assert.match(source, /EXTERNAL_SIDE_EFFECT/)
})

test('小叶工作台以 DSH 历史作为最终回复事实并支持会话级模型切换', () => {
  assert.doesNotMatch(source, /parse\(out\)/)
  assert.match(source, /getXiaoyeConversationModels/)
  assert.match(source, /selectXiaoyeConversationModel/)
  assert.match(source, /loadConversationModels/)
  assert.match(source, /changeConversationModel/)
  assert.match(source, /当前会话模型已切换/)
})

test('小叶工作台只将安全正文增量显示为流式消息，不展示推理增量', () => {
  assert.match(source, /chunk\?\.type\s*===\s*['"]text-delta['"]/)
  assert.doesNotMatch(source, /chunk\?\.type\s*===\s*['"]reasoning-delta['"]/)
  assert.match(source, /!item\.pending/)
})

test('小叶工作台流式更新保持消息节点稳定且后台同步不切换全屏加载态', () => {
  assert.match(source, /id:\s*`stream-\$\{conversationId\.value\s*\|\|\s*['"]active['"]\}`/)
  assert.match(source, /reconcileHistoryMessages/)
  assert.match(source, /scheduleHistorySnapshot/)
  assert.match(source, /loadHistory\(\{\s*silent\s*:\s*true\s*\}\)/)
  assert.match(source, /const showLoading\s*=\s*!silent\s*&&\s*!messages\.value\.length/)
  assert.doesNotMatch(source, /await new Promise\(resolve=>setTimeout\(resolve,500\)\);const parsed=await loadHistory\(\)/)
})

test('角色卡 Agent 仅将明确 JSON 草稿填入表单，不会直接写入角色库', () => {
  assert.match(source, /extractJsonObject/)
  assert.match(source, /applyRoleCardDraftFromReply/)
  assert.match(source, /```json 代码块输出完整草稿/)
  assert.match(source, /if\s*\(out\)\s*applyRoleCardDraftFromReply\(out\)/)
  assert.match(source, /保存前仍可修改/)
})

test('角色卡自然语言和 Markdown 预览也会填入右侧草稿', () => {
  assert.match(source, /roleCardPreviewFields/)
  assert.match(source, /角色名称|名称/)
  assert.match(source, /背景与场景/)
  assert.match(source, /示例对话/)
  assert.match(source, /const table\s*=\s*line\.match/)
  assert.match(source, /已将角色卡预览填入右侧草稿/)
})

test('重新打开历史会话也会恢复角色卡草稿', () => {
  assert.match(source, /for\s*\(const item of \[\.\.\.parsed\]\s*\.reverse\(\)\)/)
  assert.match(source, /applyRoleCardDraftFromReply\(item\.text\s*,\s*\{\s*notify\s*:\s*sending\.value\s*\}\)/)
  assert.match(source, /function applyRoleCardDraftFromReply\(reply\s*,\s*\{\s*notify\s*=\s*true\s*\}\s*=\s*\{\}\)/)
  assert.match(source, /roleCardDraftSource/)
})

test('小叶工作台看板娘由 DSH 运行状态驱动并允许本地隐藏', () => {
  assert.match(source, /<XiaoyeCompanion\s+:state="petState"\s+:message="petMessage"/)
  assert.match(source, /questionRequests\.value\.length\s*\|\|\s*approvalRequests\.value\.length/)
  assert.match(source, /activity\.value\.some\(\(item\)\s*=>\s*item\.state\s*===\s*"waiting"\)/)
  assert.match(source, /sending\.value/)
  assert.match(companionSource, /xiaoye:companion:visible/)
  assert.match(companionSource, /prefers-reduced-motion:\s*reduce/)
  assert.match(companionSource, /\/live2d\/models\/ug\/ugofficial\.model3\.json/)
  for (const state of ['idle', 'thinking', 'working', 'waiting', 'review', 'success', 'failed']) {
    assert.match(companionSource, new RegExp(`${state}:`))
  }
})

test('小叶工作台采用白灰原生配色并覆盖旧的整页绿色主题', () => {
  assert.match(source, /workbench-polish\.css/)
  assert.match(polishSource, /--xy-surface:\s*#ffffff/)
  assert.match(polishSource, /--xy-bg:\s*#f5f6f7/)
  assert.match(polishSource, /\.message\.assistant\s*>\s*div/)
  assert.doesNotMatch(polishSource, /gradient\(/)
})

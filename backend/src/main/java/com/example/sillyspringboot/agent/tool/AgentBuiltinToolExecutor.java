package com.example.sillyspringboot.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class AgentBuiltinToolExecutor {

    private final ObjectMapper objectMapper;
    private final AgentCharacterTool characterTool;
    private final AgentCharacterPublicationTool characterPublicationTool;
    private final AgentWorldbookTool worldbookTool;
    private final AgentStoryTool storyTool;
    private final AgentStoryRevisionTool storyRevisionTool;
    private final AgentStoryContinuityTool continuityTool;
    private final AgentImageTool imageTool;
    private final AgentTtsTool ttsTool;
    private final AgentHtmlTool htmlTool;
    private final AgentPackageTool packageTool;
    private final AgentAnalyticsTool analyticsTool;
    private final AgentSystemInspectionTool systemInspectionTool;
    private final AgentReportTool reportTool;
    private final AgentApiRequestTool apiRequestTool;
    private final AgentWebSearchTool webSearchTool;
    private final AgentMcpCallTool mcpCallTool;
    private final AgentCodeRunnerTool codeRunnerTool;
    private final AgentEvaluationTool evaluationTool;
    private final AgentUserTicketQueryTool userTicketQueryTool;
    private final AgentUserAccountQueryTool userAccountQueryTool;
    private final AgentCollaborationTool collaborationTool;
    private final AgentUserProfileQueryTool userProfileQueryTool;
    private final AgentUserRiskQueryTool userRiskQueryTool;
    private final AgentContentModerationTool contentModerationTool;
    private final AgentBrowserOpenTool browserOpenTool;
    private final AgentUserTicketReplyTool userTicketReplyTool;
    private final AgentUserAccountCreditTool userAccountCreditTool;
    private final AgentContentModerationActionTool contentModerationActionTool;
    private final AgentDshPromptTool dshPromptTool;
    private final AgentDshSessionTool dshSessionTool;
    private final AgentDshBrowserTool dshBrowserTool;
    private final AgentDshDevopsInspectTool dshDevopsInspectTool;
    private final AgentDshDevopsBuildTool dshDevopsBuildTool;
    private final AgentDshMonitorTool dshMonitorTool;
    private final AgentDshEvaluateTool dshEvaluateTool;
    private final AgentDshBackupTool dshBackupTool;
    private final AgentDshSecretStatusTool dshSecretStatusTool;
    private final AgentDshBrowserInteractTool dshBrowserInteractTool;
    private final AgentDshDevopsReleaseTool dshDevopsReleaseTool;

    public AgentBuiltinToolExecutor(
            ObjectMapper objectMapper,
            AgentCharacterTool characterTool,
            AgentCharacterPublicationTool characterPublicationTool,
            AgentWorldbookTool worldbookTool,
            AgentStoryTool storyTool,
            AgentStoryRevisionTool storyRevisionTool,
            AgentStoryContinuityTool continuityTool,
            AgentImageTool imageTool,
            AgentTtsTool ttsTool,
            AgentHtmlTool htmlTool,
            AgentPackageTool packageTool,
            AgentAnalyticsTool analyticsTool,
            AgentSystemInspectionTool systemInspectionTool,
            AgentReportTool reportTool,
            AgentApiRequestTool apiRequestTool,
            AgentWebSearchTool webSearchTool,
            AgentMcpCallTool mcpCallTool,
            AgentCodeRunnerTool codeRunnerTool,
            AgentEvaluationTool evaluationTool,
            AgentUserTicketQueryTool userTicketQueryTool,
            AgentUserAccountQueryTool userAccountQueryTool,
            AgentCollaborationTool collaborationTool,
            AgentUserProfileQueryTool userProfileQueryTool,
            AgentUserRiskQueryTool userRiskQueryTool,
            AgentContentModerationTool contentModerationTool,
            AgentBrowserOpenTool browserOpenTool,
            AgentUserTicketReplyTool userTicketReplyTool,
            AgentUserAccountCreditTool userAccountCreditTool,
            AgentContentModerationActionTool contentModerationActionTool,
            AgentDshPromptTool dshPromptTool,
            AgentDshSessionTool dshSessionTool,
            AgentDshBrowserTool dshBrowserTool,
            AgentDshDevopsInspectTool dshDevopsInspectTool,
            AgentDshDevopsBuildTool dshDevopsBuildTool,
            AgentDshMonitorTool dshMonitorTool,
            AgentDshEvaluateTool dshEvaluateTool,
            AgentDshBackupTool dshBackupTool,
            AgentDshSecretStatusTool dshSecretStatusTool,
            AgentDshBrowserInteractTool dshBrowserInteractTool,
            AgentDshDevopsReleaseTool dshDevopsReleaseTool
    ) {
        this.objectMapper = objectMapper;
        this.characterTool = characterTool;
        this.characterPublicationTool = characterPublicationTool;
        this.worldbookTool = worldbookTool;
        this.storyTool = storyTool;
        this.storyRevisionTool = storyRevisionTool;
        this.continuityTool = continuityTool;
        this.imageTool = imageTool;
        this.ttsTool = ttsTool;
        this.htmlTool = htmlTool;
        this.packageTool = packageTool;
        this.analyticsTool = analyticsTool;
        this.systemInspectionTool = systemInspectionTool;
        this.reportTool = reportTool;
        this.apiRequestTool = apiRequestTool;
        this.webSearchTool = webSearchTool;
        this.mcpCallTool = mcpCallTool;
        this.codeRunnerTool = codeRunnerTool;
        this.evaluationTool = evaluationTool;
        this.userTicketQueryTool = userTicketQueryTool;
        this.userAccountQueryTool = userAccountQueryTool;
        this.collaborationTool = collaborationTool;
        this.userProfileQueryTool = userProfileQueryTool;
        this.userRiskQueryTool = userRiskQueryTool;
        this.contentModerationTool = contentModerationTool;
        this.browserOpenTool = browserOpenTool;
        this.userTicketReplyTool = userTicketReplyTool;
        this.userAccountCreditTool = userAccountCreditTool;
        this.contentModerationActionTool = contentModerationActionTool;
        this.dshPromptTool = dshPromptTool;
        this.dshSessionTool = dshSessionTool;
        this.dshBrowserTool = dshBrowserTool;
        this.dshDevopsInspectTool = dshDevopsInspectTool;
        this.dshDevopsBuildTool = dshDevopsBuildTool;
        this.dshMonitorTool = dshMonitorTool;
        this.dshEvaluateTool = dshEvaluateTool;
        this.dshBackupTool = dshBackupTool;
        this.dshSecretStatusTool = dshSecretStatusTool;
        this.dshBrowserInteractTool = dshBrowserInteractTool;
        this.dshDevopsReleaseTool = dshDevopsReleaseTool;
    }

    public ToolResult execute(Map<String, Object> step, Map<String, Object> task) {
        String toolName = text(step.get("toolName"));
        if ("planner.analyze".equals(toolName)) {
            Map<String, Object> output = new LinkedHashMap<>();
            output.put("tool", toolName);
            output.put("taskTitle", text(task.get("taskTitle")));
            output.put("objective", text(task.get("taskObjective")));
            output.put("step", text(step.get("stepName")));
            output.put("result", "需求已结构化，等待专业工具继续执行");
            try {
                return new ToolResult(
                        objectMapper.writeValueAsString(output),
                        "已完成基础需求分析"
                );
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("需求分析结果格式化失败", e);
            }
        }
        if ("character.create".equals(toolName)) {
            return jsonResult(characterTool.create(step, task), "已创建 Agent 角色草稿");
        }
        if ("character.validate".equals(toolName)) {
            return jsonResult(characterTool.validate(step, task), "已完成角色质量校验");
        }
        if ("character.publish".equals(toolName)) {
            return jsonResult(characterPublicationTool.publish(step, task), "已发布 Agent 角色");
        }
        if ("character.sync_st".equals(toolName)) {
            return jsonResult(characterPublicationTool.syncToSt(step, task), "已同步角色卡到 SillyTavern");
        }
        if ("worldbook.create".equals(toolName)) {
            return jsonResult(worldbookTool.create(step, task), "已创建或更新 Agent 世界书");
        }
        if ("story.create_outline".equals(toolName)) {
            return jsonResult(storyTool.createOutline(step, task), "已生成剧情大纲产物");
        }
        if ("story.write_chapter".equals(toolName)) {
            return jsonResult(storyTool.writeChapters(step, task), "已生成章节正文产物");
        }
        if ("story.check_continuity".equals(toolName)) {
            return jsonResult(continuityTool.check(step, task), "已完成剧情一致性检查");
        }
        if ("story.revise".equals(toolName)) {
            return jsonResult(storyRevisionTool.reviseChapters(step, task), "已完成剧情修订处理");
        }
        if ("image.generate".equals(toolName)) {
            return jsonResult(imageTool.generate(step, task), "已生成 Agent 图片产物");
        }
        if ("tts.generate".equals(toolName)) {
            return jsonResult(ttsTool.generate(step, task), "已生成 Agent 音频产物");
        }
        if ("artifact.render_html".equals(toolName)) {
            return jsonResult(htmlTool.render(step, task), "已生成互动网页产物");
        }
        if ("artifact.package_zip".equals(toolName)) {
            return jsonResult(packageTool.packageTask(step, task), "已生成作品 ZIP 包");
        }
        if ("analytics.query".equals(toolName)) {
            Map<String, Object> output = analyticsTool.query(step, task);
            analyticsTool.saveArtifact(number(task.get("taskId")), number(step.get("stepId")), "analytics-data", "运营指标数据", output);
            return jsonResult(output, "已读取参数化运营指标");
        }
        if ("analytics.review".equals(toolName)) {
            Map<String, Object> output = analyticsTool.review(step, task);
            analyticsTool.saveArtifact(number(task.get("taskId")), number(step.get("stepId")), "analytics-review", "运营分析复核", output);
            return jsonResult(output, "已完成运营数据复核");
        }
        if ("system.health_check".equals(toolName)) {
            Map<String, Object> output = systemInspectionTool.inspect(step, task);
            systemInspectionTool.saveArtifact(number(task.get("taskId")), number(step.get("stepId")), output);
            return jsonResult(output, "已完成系统只读巡检");
        }
        if ("artifact.render_report".equals(toolName)) {
            return jsonResult(reportTool.render(step, task), "已生成 Agent 执行报告");
        }
        if ("api.request".equals(toolName)) {
            return jsonResult(apiRequestTool.request(step), "已完成白名单 API 只读请求");
        }
        if ("web.search".equals(toolName)) {
            return jsonResult(webSearchTool.search(step), "已完成白名单 Web 搜索");
        }
        if ("browser.open".equals(toolName)) {
            try {
                return new ToolResult(objectMapper.writeValueAsString(browserOpenTool.read(step)), "已完成受控网页读取");
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("网页读取结果格式化失败", ex);
            }
        }
        if ("mcp.call".equals(toolName)) {
            return jsonResult(mcpCallTool.call(step), "已完成受控 MCP 调用");
        }
        if ("dsh.session.prompt".equals(toolName)) {
            return jsonResult(dshPromptTool.prompt(step, task), "已提交审批后的 DSH Prompt");
        }
        if ("dsh.session.create".equals(toolName)) {
            return jsonResult(dshSessionTool.create(step, task), "已创建审批后的 DSH 会话");
        }
        if ("dsh.browser.open".equals(toolName)) {
            return jsonResult(dshBrowserTool.open(step), "已完成受控 DSH 浏览读取");
        }
        if ("dsh.devops.inspect".equals(toolName)) {
            return jsonResult(dshDevopsInspectTool.inspect(step, task), "已完成受控 DSH DevOps 检查");
        }
        if ("dsh.devops.build".equals(toolName)) {
            return jsonResult(dshDevopsBuildTool.build(step, task), "已完成审批后的 DSH DevOps 构建");
        }
        if (toolName.startsWith("dsh.devops.")) {
            String action = toolName.substring("dsh.devops.".length());
            if (Set.of("publish", "deploy", "health", "rollback").contains(action)) {
                return jsonResult(dshDevopsReleaseTool.run(action, step, task), "已完成审批后的 DSH DevOps " + action);
            }
        }
        if ("dsh.monitor.snapshot".equals(toolName)) {
            return jsonResult(dshMonitorTool.snapshot(step, task), "已完成受控 DSH 监控快照");
        }
        if ("dsh.evaluate.run".equals(toolName)) {
            return jsonResult(dshEvaluateTool.evaluate(task), "已完成受控 DSH 评估");
        }
        if ("dsh.backup.inspect".equals(toolName)) {
            return jsonResult(dshBackupTool.inspect(step, task), "已完成受控 DSH 备份检查");
        }
        if ("dsh.secret.status".equals(toolName)) {
            return jsonResult(dshSecretStatusTool.status(), "已完成受控 DSH 凭据状态检查");
        }
        if ("dsh.secret.lease".equals(toolName)) {
            return jsonResult(dshSecretStatusTool.lease(step, task), "已创建短期 DSH 凭据租约引用");
        }
        if ("dsh.browser.interact".equals(toolName)) {
            return jsonResult(dshBrowserInteractTool.interact(step, task), "已完成审批后的 DSH Browser 操作");
        }
        if ("code.run".equals(toolName)) {
            return jsonResult(codeRunnerTool.run(step), "已完成隔离代码执行");
        }
        if ("artifact.evaluate".equals(toolName)) {
            return jsonResult(evaluationTool.evaluate(task), "已完成 Agent 任务确定性评估");
        }
        if ("user.ticket.query".equals(toolName)) {
            return jsonResult(userTicketQueryTool.query(step), "已读取用户工单摘要");
        }
        if ("user.ticket.reply".equals(toolName)) {
            return jsonResult(userTicketReplyTool.reply(step), "已完成审批后的工单回复");
        }
        if ("user.account.query".equals(toolName)) {
            return jsonResult(userAccountQueryTool.query(step), "已读取用户账户摘要");
        }
        if ("user.account.credit".equals(toolName)) {
            return jsonResult(userAccountCreditTool.credit(step, task), "已完成审批后的钱包补发");
        }
        if ("agent.handoff".equals(toolName)) {
            return jsonResult(collaborationTool.handoff(step, task), "已记录 Agent 角色协作交接");
        }
        if ("user.profile.query".equals(toolName)) {
            return jsonResult(userProfileQueryTool.query(step), "已读取用户资料摘要");
        }
        if ("user.risk.query".equals(toolName)) {
            return jsonResult(userRiskQueryTool.query(step), "已读取用户风险摘要");
        }
        if ("content.moderation.review".equals(toolName)) {
            return jsonResult(contentModerationTool.review(step), "已完成角色内容只读复核");
        }
        if ("content.moderation.action".equals(toolName)) {
            return jsonResult(contentModerationActionTool.review(step), "已完成审批后的角色审核动作");
        }
        throw new UnsupportedOperationException("工具未注册或尚未实现: " + (toolName.isBlank() ? "unknown" : toolName));
    }

    private ToolResult jsonResult(Map<String, Object> output, String summary) {
        try {
            return new ToolResult(objectMapper.writeValueAsString(output), summary);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Agent 工具结果格式化失败", e);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public record ToolResult(String outputJson, String outputSummary) {}
}

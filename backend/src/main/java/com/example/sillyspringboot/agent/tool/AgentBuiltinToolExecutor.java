package com.example.sillyspringboot.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

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
            AgentReportTool reportTool
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
            return jsonResult(systemInspectionTool.inspect(step, task), "已完成系统只读巡检");
        }
        if ("artifact.render_report".equals(toolName)) {
            return jsonResult(reportTool.render(step, task), "已生成 Agent 执行报告");
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

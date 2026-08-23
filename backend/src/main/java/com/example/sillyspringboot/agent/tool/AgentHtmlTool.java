package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.example.sillyspringboot.agent.service.AgentArtifactStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Renders a deterministic, self-contained interactive story page. */
@Service
public class AgentHtmlTool {

    private final AgentAdminMapper mapper;
    private final ObjectMapper objectMapper;
    private final AgentArtifactStorageService storageService;

    public AgentHtmlTool(
            AgentAdminMapper mapper,
            ObjectMapper objectMapper,
            AgentArtifactStorageService storageService
    ) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.storageService = storageService;
    }

    @Transactional
    public Map<String, Object> render(Map<String, Object> step, Map<String, Object> task) {
        long taskId = number(task.get("taskId"));
        long stepId = number(step.get("stepId"));
        Map<String, Object> outlineArtifact = mapper.findArtifact(taskId, "story-outline", 1);
        if (outlineArtifact == null || text(outlineArtifact.get("contentJson")).isBlank()) {
            throw new IllegalArgumentException("尚未找到可渲染的剧情大纲");
        }
        Map<String, Object> chaptersArtifact = mapper.findArtifact(taskId, "story-chapters", 1);
        if (chaptersArtifact == null || text(chaptersArtifact.get("contentJson")).isBlank()) {
            throw new IllegalArgumentException("尚未找到可渲染的章节正文");
        }
        JsonNode outline = parse(text(outlineArtifact.get("contentJson")), "剧情大纲");
        JsonNode chapters = parse(text(chaptersArtifact.get("contentJson")), "章节正文");
        List<Map<String, Object>> media = mapper.listArtifacts(taskId).stream()
                .filter(row -> "IMAGE".equalsIgnoreCase(text(row.get("artifactType")))
                        || "AUDIO".equalsIgnoreCase(text(row.get("artifactType"))))
                .filter(row -> "READY".equalsIgnoreCase(text(row.get("status"))))
                .toList();
        String html = renderHtml(task, outline, chapters, media);
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        AgentArtifactStorageService.StoredFile stored = storageService.storeHtml(taskId, bytes);

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("taskId", taskId);
        artifact.put("stepId", stepId);
        artifact.put("artifactKey", "interactive-html");
        artifact.put("artifactType", "HTML");
        artifact.put("name", "index.html");
        artifact.put("version", 1);
        artifact.put("status", "READY");
        artifact.put("uri", stored.uri());
        artifact.put("contentHash", stored.contentHash());
        artifact.put("sizeBytes", stored.sizeBytes());
        artifact.put("mimeType", "text/html; charset=UTF-8");
        artifact.put("metadataJson", json(Map.of(
                "generatedBy", "AGENT_HTML_TOOL",
                "schemaVersion", 1,
                "nodeCount", outline.path("nodes").isArray() ? outline.path("nodes").size() : 0,
                "chapterCount", chapters.path("chapters").isArray() ? chapters.path("chapters").size() : 0,
                "mediaCount", media.size()
        )));
        artifact.put("contentJson", json(Map.of(
                "operation", "RENDERED",
                "artifactKey", "interactive-html",
                "uri", stored.uri(),
                "contentHash", stored.contentHash(),
                "sizeBytes", stored.sizeBytes()
        )));
        if (mapper.upsertArtifact(artifact) < 1) {
            throw new IllegalStateException("互动网页产物记录保存失败");
        }
        mapper.refreshArtifactCount(taskId);

        return Map.of(
                "operation", "RENDERED",
                "artifactKey", "interactive-html",
                "uri", stored.uri(),
                "contentHash", stored.contentHash(),
                "sizeBytes", stored.sizeBytes()
        );
    }

    private String renderHtml(Map<String, Object> task, JsonNode outline, JsonNode chapters, List<Map<String, Object>> media) {
        String title = firstNonBlank(outline.path("title").asText(""), text(task.get("taskTitle")), "Agent 互动故事");
        String startNode = firstNonBlank(outline.path("startNodeId").asText(""), "chapter_1");
        List<String> nodes = new ArrayList<>();
        JsonNode rawNodes = outline.path("nodes");
        if (rawNodes.isArray()) {
            rawNodes.forEach(node -> nodes.add(renderNode(node, chapters)));
        }
        String nodeJson = safeScriptJson(rawNodes.isArray() ? rawNodes : objectMapper.createArrayNode());
        StringBuilder html = new StringBuilder(16_000);
        html.append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>").append(escape(title)).append("</title><style>")
                .append("*{box-sizing:border-box}body{margin:0;background:#101827;color:#e7edf6;font:16px/1.7 system-ui,-apple-system,BlinkMacSystemFont,Segoe UI,sans-serif}")
                .append("main{max-width:980px;margin:0 auto;padding:40px 20px 64px}.eyebrow{color:#7dd3fc;text-transform:uppercase;letter-spacing:.08em;font-size:12px}")
                .append("h1{font-size:clamp(28px,5vw,52px);line-height:1.15;margin:8px 0 28px}.story{background:#172235;border:1px solid #2b3b55;border-radius:8px;padding:24px;box-shadow:0 18px 50px #050a1280}")
                .append(".node{display:none}.node.is-active{display:block}.node h2{margin:0 0 12px;font-size:26px}.node p{white-space:pre-wrap}.choices{display:flex;flex-wrap:wrap;gap:10px;margin-top:22px}.choices button{border:1px solid #38bdf8;background:#0f2c46;color:#e0f2fe;border-radius:6px;padding:10px 16px;cursor:pointer}.choices button:hover{background:#164e70}.ending{color:#fcd34d;font-weight:600}")
                .append(".media{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:16px;margin-top:26px}.media figure{margin:0}.media img{display:block;width:100%;max-height:360px;object-fit:cover;border-radius:6px}.media figcaption{color:#aebbd0;font-size:13px;margin-top:6px}.media audio{width:100%;margin-top:8px}.footer{margin-top:22px;color:#8292aa;font-size:13px}")
                .append("</style></head><body><main><div class=\"eyebrow\">AiSillyTavern Agent Work</div><h1>")
                .append(escape(title)).append("</h1><section class=\"story\" id=\"story\">");
        for (String node : nodes) html.append(node);
        html.append("</section>");
        appendMedia(html, media);
        html.append("<div class=\"footer\">由 Agent 任务自动生成，内容和媒体来源记录在作品包 manifest 中。</div></main><script>")
                .append("const outlineNodes=").append(nodeJson).append(";const startNode=").append(json(startNode)).append(";")
                .append("function showNode(id){document.querySelectorAll('.node').forEach(n=>n.classList.toggle('is-active',n.dataset.nodeId===id));window.scrollTo({top:0,behavior:'smooth'});}document.addEventListener('click',e=>{const b=e.target.closest('[data-target]');if(b)showNode(b.dataset.target);});showNode(startNode);")
                .append("</script></body></html>");
        return html.toString();
    }

    private String renderNode(JsonNode node, JsonNode chapters) {
        String id = firstNonBlank(node.path("nodeId").asText(""), "node");
        String title = firstNonBlank(node.path("title").asText(""), node.path("name").asText(""), id);
        String content = firstNonBlank(node.path("content").asText(""), node.path("text").asText(""), chapterBody(chapters, id), node.path("summary").asText(""));
        StringBuilder html = new StringBuilder();
        html.append("<article class=\"node\" data-node-id=\"").append(escapeAttribute(id)).append("\">")
                .append("<h2>").append(escape(title)).append("</h2>");
        if (!content.isBlank()) html.append("<p>").append(escape(content)).append("</p>");
        if (node.path("terminal").asBoolean(false) || "ENDING".equalsIgnoreCase(node.path("nodeType").asText(""))) {
            html.append("<p class=\"ending\">故事结局</p>");
        }
        html.append("<div class=\"choices\">");
        JsonNode choices = node.path("choices");
        if (choices.isArray()) {
            choices.forEach(choice -> {
                String label = firstNonBlank(choice.path("label").asText(""), "继续");
                String target = choice.path("targetNodeId").asText("");
                if (!target.isBlank()) {
                    html.append("<button type=\"button\" data-target=\"")
                            .append(escapeAttribute(target)).append("\">")
                            .append(escape(label)).append("</button>");
                }
            });
        }
        html.append("</div></article>");
        return html.toString();
    }

    private String chapterBody(JsonNode chapters, String nodeId) {
        JsonNode items = chapters == null ? null : chapters.path("chapters");
        if (items == null || !items.isArray()) return "";
        for (JsonNode chapter : items) {
            if (nodeId.equals(chapter.path("nodeId").asText(""))) {
                return chapter.path("body").asText("");
            }
        }
        return "";
    }

    private void appendMedia(StringBuilder html, List<Map<String, Object>> media) {
        if (media.isEmpty()) return;
        html.append("<section class=\"media\" aria-label=\"媒体素材\">");
        for (Map<String, Object> item : media) {
            String type = text(item.get("artifactType"));
            String uri = text(item.get("uri"));
            String name = firstNonBlank(text(item.get("name")), text(item.get("artifactKey")));
            if ("IMAGE".equalsIgnoreCase(type)) {
                html.append("<figure><img loading=\"lazy\" src=\"").append(escapeAttribute(uri))
                        .append("\" alt=\"").append(escapeAttribute(name)).append("\"><figcaption>")
                        .append(escape(name)).append("</figcaption></figure>");
            } else if ("AUDIO".equalsIgnoreCase(type)) {
                html.append("<figure><figcaption>").append(escape(name)).append("</figcaption><audio controls preload=\"metadata\" src=\"")
                        .append(escapeAttribute(uri)).append("\"></audio></figure>");
            }
        }
        html.append("</section>");
    }

    private JsonNode parse(String value, String label) {
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node == null || !node.isObject()) throw new IllegalArgumentException(label + "必须是 JSON 对象");
            return node;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException(label + " JSON 无效", ex);
        }
    }

    private String safeScriptJson(JsonNode value) {
        return json(value).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026");
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("互动网页 JSON 格式化失败", ex); }
    }

    private static String escape(String value) {
        return text(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String escapeAttribute(String value) { return escape(value); }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (!text(value).isBlank()) return text(value);
        return "";
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private static long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return Long.parseLong(text(value)); } catch (NumberFormatException ex) { return 0L; }
    }
}

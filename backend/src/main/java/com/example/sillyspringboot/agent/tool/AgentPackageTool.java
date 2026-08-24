package com.example.sillyspringboot.agent.tool;

import com.example.sillyspringboot.agent.mapper.AgentAdminMapper;
import com.example.sillyspringboot.agent.service.AgentArtifactStorageService;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.ops.service.ImageGenerationAssetStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Packages the validated Agent content outputs into one downloadable ZIP. */
@Service
public class AgentPackageTool {

    private final AgentAdminMapper mapper;
    private final AppCharacterMapper characterMapper;
    private final AgentArtifactStorageService storageService;
    private final ImageGenerationAssetStorageService imageStorage;
    private final ObjectMapper objectMapper;

    public AgentPackageTool(
            AgentAdminMapper mapper,
            AppCharacterMapper characterMapper,
            AgentArtifactStorageService storageService,
            ImageGenerationAssetStorageService imageStorage,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.characterMapper = characterMapper;
        this.storageService = storageService;
        this.imageStorage = imageStorage;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> packageTask(Map<String, Object> step, Map<String, Object> task) {
        long taskId = number(task.get("taskId"));
        Map<String, Object> outline = requireArtifact(taskId, "story-outline", "剧情大纲");
        Map<String, Object> chapters = requireArtifact(taskId, "story-chapters", "章节正文");
        Map<String, Object> continuity = requireArtifact(taskId, "continuity-report", "一致性报告");
        JsonNode continuityJson = parseObject(text(continuity.get("contentJson")), "一致性报告");
        if (!continuityJson.path("passed").asBoolean(false)) {
            throw new IllegalStateException("剧情一致性检查未通过，不能打包交付");
        }

        List<Map<String, Object>> characters = loadCharacters(task.get("completedSteps"));
        Map<String, Object> worldbook = mapper.findArtifact(taskId, "worldbook", 1);
        if (worldbook == null) {
            throw new IllegalArgumentException("尚未找到世界书产物");
        }
        List<Map<String, Object>> imageArtifacts = loadImageArtifacts(taskId);
        List<Map<String, Object>> audioArtifacts = loadAudioArtifacts(taskId);
        Map<String, Object> htmlArtifact = loadHtmlArtifact(taskId);

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("schemaVersion", 1);
        manifest.put("packageType", "AISILLYTAVERN_AGENT_WORK");
        manifest.put("taskId", taskId);
        manifest.put("taskKey", text(task.get("taskKey")));
        manifest.put("title", text(task.get("taskTitle")));
        manifest.put("objective", text(task.get("taskObjective")));
        manifest.put("createdAt", text(task.get("createdAt")));
        manifest.put("generatedAt", LocalDateTime.now().toString());
        List<String> files = new ArrayList<>(List.of(
                "manifest.json",
                "characters/characters.json",
                "worldbook/worldbook.json",
                "story/outline.json",
                "story/chapters.json",
                "review/continuity-report.json",
                "README.md"
        ));
        List<Map<String, Object>> sourceArtifacts = new ArrayList<>(List.of(
                artifactSummary(worldbook),
                artifactSummary(outline),
                artifactSummary(chapters),
                artifactSummary(continuity)
        ));
        for (Map<String, Object> image : imageArtifacts) {
            files.add(imageFileName(image));
            sourceArtifacts.add(artifactSummary(image));
        }
        for (Map<String, Object> audio : audioArtifacts) {
            files.add(audioFileName(audio));
            sourceArtifacts.add(artifactSummary(audio));
        }
        if (htmlArtifact != null) {
            files.add("index.html");
            sourceArtifacts.add(artifactSummary(htmlArtifact));
        }
        if (!imageArtifacts.isEmpty()) {
            files.add("images/images.json");
        }
        if (!audioArtifacts.isEmpty()) {
            files.add("audio/audio.json");
        }
        manifest.put("files", files);
        manifest.put("sourceArtifacts", sourceArtifacts);

        byte[] zipBytes = buildZip(manifest, characters, worldbook, outline, chapters, continuity, imageArtifacts, audioArtifacts, htmlArtifact);
        verifyZip(zipBytes, files);
        AgentArtifactStorageService.StoredFile stored = storageService.storeZip(taskId, zipBytes);
        String manifestJson = json(manifest);

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("taskId", taskId);
        artifact.put("stepId", number(step.get("stepId")));
        artifact.put("artifactKey", "task-package");
        artifact.put("artifactType", "PACKAGE_ZIP");
        artifact.put("name", text(task.get("taskTitle")).isBlank() ? "Agent 作品包.zip" : text(task.get("taskTitle")) + ".zip");
        artifact.put("version", 1);
        artifact.put("status", "READY");
        artifact.put("uri", stored.uri());
        artifact.put("contentHash", stored.contentHash());
        artifact.put("sizeBytes", stored.sizeBytes());
        artifact.put("mimeType", "application/zip");
        artifact.put("metadataJson", json(Map.of(
                "packageType", "AISILLYTAVERN_AGENT_WORK",
                "schemaVersion", 1,
                "fileCount", files.size()
        )));
        artifact.put("contentJson", manifestJson);
        if (mapper.upsertArtifact(artifact) < 1) {
            throw new IllegalStateException("作品包产物记录保存失败");
        }
        mapper.refreshArtifactCount(taskId);
        Map<String, Object> persisted = mapper.findArtifact(taskId, "task-package", 1);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("operation", "PACKAGED");
        output.put("artifactId", persisted == null ? null : persisted.get("id"));
        output.put("artifactKey", "task-package");
        output.put("uri", stored.uri());
        output.put("contentHash", stored.contentHash());
        output.put("sizeBytes", stored.sizeBytes());
        output.put("fileCount", files.size());
        output.put("manifest", manifest);
        return output;
    }

    private byte[] buildZip(
            Map<String, Object> manifest,
            List<Map<String, Object>> characters,
            Map<String, Object> worldbook,
            Map<String, Object> outline,
            Map<String, Object> chapters,
            Map<String, Object> continuity,
            List<Map<String, Object>> imageArtifacts,
            List<Map<String, Object>> audioArtifacts,
            Map<String, Object> htmlArtifact
    ) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            addText(zip, "manifest.json", json(manifest));
            addText(zip, "characters/characters.json", json(characters));
            addText(zip, "worldbook/worldbook.json", text(worldbook.get("contentJson")));
            addText(zip, "story/outline.json", text(outline.get("contentJson")));
            addText(zip, "story/chapters.json", text(chapters.get("contentJson")));
            addText(zip, "review/continuity-report.json", text(continuity.get("contentJson")));
            if (!imageArtifacts.isEmpty()) {
                addText(zip, "images/images.json", json(imageArtifacts.stream()
                        .map(this::imageManifest)
                        .toList()));
                for (Map<String, Object> image : imageArtifacts) {
                    addImageBinary(zip, imageFileName(image), image);
                }
            }
            if (!audioArtifacts.isEmpty()) {
                addText(zip, "audio/audio.json", json(audioArtifacts.stream()
                        .map(this::audioManifest)
                        .toList()));
                for (Map<String, Object> audio : audioArtifacts) {
                    addAudioBinary(zip, manifest, audioFileName(audio), audio);
                }
            }
            if (htmlArtifact != null) {
                addHtmlBinary(zip, htmlArtifact, imageArtifacts, audioArtifacts);
            }
            addText(zip, "README.md", readme(manifest));
            zip.finish();
            return bytes.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("作品 ZIP 生成失败", e);
        }
    }

    /** Validate the bytes before persisting a package artifact. */
    static void verifyZip(byte[] bytes, List<String> requiredFiles) {
        if (bytes == null || bytes.length == 0) throw new IllegalStateException("作品 ZIP 为空");
        Set<String> entries = new java.util.HashSet<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                if (name == null || name.isBlank() || !entries.add(name)) {
                    throw new IllegalStateException("作品 ZIP 存在空文件名或重复条目");
                }
                input.transferTo(OutputStream.nullOutputStream());
            }
        } catch (IOException ex) {
            throw new IllegalStateException("作品 ZIP 完整性校验失败", ex);
        }
        if (entries.isEmpty()) throw new IllegalStateException("作品 ZIP 不包含任何文件");
        if (requiredFiles != null) {
            for (String required : requiredFiles) {
                if (required == null || required.isBlank() || !entries.contains(required)) {
                    throw new IllegalStateException("作品 ZIP 缺少必需文件: " + required);
                }
            }
        }
    }

    private List<Map<String, Object>> loadCharacters(Object completedSteps) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (completedSteps instanceof List<?> rows) {
            for (Object row : rows) {
                if (!(row instanceof Map<?, ?> values) || !"character.create".equals(text(values.get("toolName")))) {
                    continue;
                }
                JsonNode output = parseObject(text(values.get("outputJson")), "角色结果");
                List<Long> characterIds = new ArrayList<>();
                JsonNode rawIds = output.get("characterIds");
                if (rawIds != null && rawIds.isArray()) {
                    rawIds.forEach(item -> {
                        long id = item.asLong(0L);
                        if (id > 0 && !characterIds.contains(id)) characterIds.add(id);
                    });
                }
                long singleId = output.path("characterId").asLong(0L);
                if (singleId > 0 && !characterIds.contains(singleId)) characterIds.add(singleId);
                for (Long characterId : characterIds) {
                    AppCharacter character = characterMapper.findById(characterId);
                    if (character == null || character.getDeletedAt() != null) {
                        throw new IllegalStateException("角色产物对应的角色不存在: " + characterId);
                    }
                    result.add(characterManifest(character));
                }
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("尚未找到角色产物");
        }
        return result;
    }

    private Map<String, Object> characterManifest(AppCharacter character) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", character.getId());
        value.put("stAvatarUrl", character.getStAvatarUrl());
        value.put("name", character.getName());
        value.put("description", character.getDescription());
        value.put("tagline", character.getTagline());
        value.put("bio", character.getBio());
        value.put("persona", character.getPersona());
        value.put("scenario", character.getScenario());
        value.put("firstMessage", character.getFirstMessage());
        value.put("alternateGreetingsJson", character.getAlternateGreetingsJson());
        value.put("mesExample", character.getMesExample());
        value.put("systemPrompt", character.getSystemPrompt());
        value.put("postHistoryInstructions", character.getPostHistoryInstructions());
        value.put("avatarUrl", character.getAvatarUrl());
        value.put("coverUrl", character.getCoverUrl());
        value.put("chatBackgroundUrl", character.getChatBackgroundUrl());
        value.put("stWorldNamesJson", character.getStWorldNamesJson());
        value.put("occupationLabel", character.getOccupationLabel());
        value.put("tagsJson", character.getTagsJson());
        value.put("gameplayType", character.getGameplayType());
        value.put("chatModesJson", character.getChatModesJson());
        value.put("reviewStatus", character.getReviewStatus());
        value.put("clientVisible", character.getClientVisible());
        value.put("previewBlurVipLevel", character.getPreviewBlurVipLevel());
        value.put("healthScore", character.getHealthScore());
        value.put("healthIssuesJson", character.getHealthIssuesJson());
        // Keep Java time values out of the package JSON contract.
        value.put("createdAt", text(character.getCreatedAt()));
        value.put("updatedAt", text(character.getUpdatedAt()));
        return value;
    }

    private List<Map<String, Object>> loadImageArtifacts(long taskId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> artifact : mapper.listArtifacts(taskId)) {
            if (!"IMAGE".equalsIgnoreCase(text(artifact.get("artifactType")))) {
                continue;
            }
            if (!"READY".equalsIgnoreCase(text(artifact.get("status")))) {
                throw new IllegalStateException("图片产物尚未就绪: " + text(artifact.get("artifactKey")));
            }
            if (!text(artifact.get("uri")).startsWith("/uploads/generated/")) {
                throw new IllegalArgumentException("图片产物地址不受 Agent 存储控制: " + text(artifact.get("artifactKey")));
            }
            result.add(artifact);
        }
        result.sort((left, right) -> text(left.get("artifactKey")).compareTo(text(right.get("artifactKey"))));
        return result;
    }

    private Map<String, Object> imageManifest(Map<String, Object> artifact) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifactKey", artifact.get("artifactKey"));
        result.put("name", artifact.get("name"));
        result.put("file", imageFileName(artifact));
        result.put("mimeType", artifact.get("mimeType"));
        result.put("sizeBytes", artifact.get("sizeBytes"));
        result.put("contentHash", artifact.get("contentHash"));
        result.put("metadataJson", artifact.get("metadataJson"));
        return result;
    }

    private String imageFileName(Map<String, Object> artifact) {
        String key = text(artifact.get("artifactKey"));
        if (!key.matches("image-[1-4]")) {
            throw new IllegalArgumentException("图片产物编号不合法: " + key);
        }
        return "images/" + key + "." + extension(artifact.get("mimeType"));
    }

    private List<Map<String, Object>> loadAudioArtifacts(long taskId) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> artifact : mapper.listArtifacts(taskId)) {
            if (!"AUDIO".equalsIgnoreCase(text(artifact.get("artifactType")))) {
                continue;
            }
            if (!"READY".equalsIgnoreCase(text(artifact.get("status")))) {
                throw new IllegalStateException("音频产物尚未就绪: " + text(artifact.get("artifactKey")));
            }
            if (!text(artifact.get("uri")).startsWith("/uploads/agent/")) {
                throw new IllegalArgumentException("音频产物地址不受 Agent 存储控制: " + text(artifact.get("artifactKey")));
            }
            result.add(artifact);
        }
        result.sort((left, right) -> text(left.get("artifactKey")).compareTo(text(right.get("artifactKey"))));
        return result;
    }

    private Map<String, Object> loadHtmlArtifact(long taskId) {
        Map<String, Object> artifact = mapper.findArtifact(taskId, "interactive-html", 1);
        if (artifact == null) {
            return null;
        }
        if (!"READY".equalsIgnoreCase(text(artifact.get("status")))) {
            throw new IllegalStateException("互动网页产物尚未就绪");
        }
        if (!text(artifact.get("uri")).startsWith("/uploads/agent/")) {
            throw new IllegalArgumentException("互动网页产物地址不受 Agent 存储控制");
        }
        return artifact;
    }

    private Map<String, Object> audioManifest(Map<String, Object> artifact) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("artifactKey", artifact.get("artifactKey"));
        result.put("name", artifact.get("name"));
        result.put("file", audioFileName(artifact));
        result.put("mimeType", artifact.get("mimeType"));
        result.put("sizeBytes", artifact.get("sizeBytes"));
        result.put("contentHash", artifact.get("contentHash"));
        result.put("metadataJson", artifact.get("metadataJson"));
        return result;
    }

    private String audioFileName(Map<String, Object> artifact) {
        String key = text(artifact.get("artifactKey"));
        if (!key.matches("audio-[1-4]")) {
            throw new IllegalArgumentException("音频产物编号不合法: " + key);
        }
        return "audio/" + key + "." + audioExtension(artifact.get("mimeType"));
    }

    private Map<String, Object> requireArtifact(long taskId, String key, String label) {
        Map<String, Object> artifact = mapper.findArtifact(taskId, key, 1);
        if (artifact == null || !"READY".equalsIgnoreCase(text(artifact.get("status")))) {
            throw new IllegalArgumentException("尚未找到可用的" + label + "产物");
        }
        if (text(artifact.get("contentJson")).isBlank()) {
            throw new IllegalArgumentException(label + "产物内容为空");
        }
        return artifact;
    }

    private Map<String, Object> artifactSummary(Map<String, Object> artifact) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("artifactId", artifact.get("id"));
        summary.put("artifactKey", artifact.get("artifactKey"));
        summary.put("artifactType", artifact.get("artifactType"));
        summary.put("version", artifact.get("version"));
        summary.put("contentHash", artifact.get("contentHash"));
        return summary;
    }

    private JsonNode parseObject(String raw, String label) {
        try {
            JsonNode value = objectMapper.readTree(raw);
            if (value == null || !value.isObject()) {
                throw new IllegalArgumentException(label + "必须是 JSON 对象");
            }
            return value;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(label + " JSON 无效", e);
        }
    }

    private void addText(ZipOutputStream zip, String name, String value) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void addImageBinary(ZipOutputStream zip, String name, Map<String, Object> artifact) throws Exception {
        Path path = imageStorage.resolveGenerated(text(artifact.get("uri")));
        byte[] bytes = Files.readAllBytes(path);
        verifyAndWriteBinary(zip, name, artifact, bytes);
    }

    private void addAudioBinary(ZipOutputStream zip, Map<String, Object> manifest, String name,
                                Map<String, Object> artifact) throws Exception {
        long taskId = number(manifest.get("taskId"));
        Path path = storageService.resolveDownload(taskId, text(artifact.get("uri")));
        byte[] bytes = Files.readAllBytes(path);
        verifyAndWriteBinary(zip, name, artifact, bytes);
    }

    private void addHtmlBinary(ZipOutputStream zip, Map<String, Object> htmlArtifact,
                               List<Map<String, Object>> imageArtifacts,
                               List<Map<String, Object>> audioArtifacts) throws Exception {
        long taskId = number(htmlArtifact.get("taskId"));
        if (taskId <= 0) {
            throw new IllegalArgumentException("互动网页产物缺少任务编号");
        }
        Path path = storageService.resolveDownload(taskId, text(htmlArtifact.get("uri")));
        byte[] bytes = Files.readAllBytes(path);
        String expectedHash = text(htmlArtifact.get("contentHash"));
        if (!expectedHash.isBlank() && !expectedHash.equalsIgnoreCase(sha256(bytes))) {
            throw new IllegalStateException("互动网页产物哈希校验失败");
        }
        String html = new String(bytes, StandardCharsets.UTF_8);
        for (Map<String, Object> image : imageArtifacts) {
            html = html.replace(text(image.get("uri")), imageFileName(image));
        }
        for (Map<String, Object> audio : audioArtifacts) {
            html = html.replace(text(audio.get("uri")), audioFileName(audio));
        }
        addText(zip, "index.html", html);
    }

    private void verifyAndWriteBinary(ZipOutputStream zip, String name,
                                      Map<String, Object> artifact, byte[] bytes) throws Exception {
        String expectedHash = text(artifact.get("contentHash"));
        if (!expectedHash.isBlank() && !expectedHash.equalsIgnoreCase(sha256(bytes))) {
            throw new IllegalStateException("媒体产物哈希校验失败: " + text(artifact.get("artifactKey")));
        }
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private String readme(Map<String, Object> manifest) {
        return "# AiSillyTavern Agent 作品包\n\n"
                + "本作品包由 Agent 任务自动整理生成。\n\n"
                + "- 任务编号: " + manifest.get("taskId") + "\n"
                + "- 任务标题: " + manifest.get("title") + "\n"
                + "- 包格式版本: " + manifest.get("schemaVersion") + "\n";
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("作品包 JSON 格式化失败", e);
        }
    }

    private static String extension(Object mimeType) {
        String value = text(mimeType).toLowerCase();
        if (value.contains("jpeg") || value.contains("jpg")) return "jpg";
        if (value.contains("webp")) return "webp";
        if (value.contains("gif")) return "gif";
        return "png";
    }

    private static String audioExtension(Object mimeType) {
        String value = text(mimeType).toLowerCase();
        if (value.contains("wav")) return "wav";
        if (value.contains("ogg")) return "ogg";
        if (value.contains("flac")) return "flac";
        if (value.contains("mp4")) return "m4a";
        if (value.contains("webm")) return "webm";
        return "mp3";
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte item : digest) out.append(String.format("%02x", item & 0xff));
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("图片哈希计算失败", ex);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(text(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}

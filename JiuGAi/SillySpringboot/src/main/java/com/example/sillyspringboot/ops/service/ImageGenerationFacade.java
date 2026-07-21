package com.example.sillyspringboot.ops.service;

import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ImageGenerationFacade {

    private final AppImageGenerationSettingsService settingsService;
    private final Map<String, ImageGenerationEngine> engines;

    public ImageGenerationFacade(
            AppImageGenerationSettingsService settingsService,
            List<ImageGenerationEngine> engines
    ) {
        this.settingsService = settingsService;
        this.engines = engines.stream().collect(Collectors.toMap(
                engine -> normalizeEngine(engine.engineName()),
                engine -> engine,
                (left, right) -> left,
                LinkedHashMap::new
        ));
    }

    public Map<String, Object> generate(String clientUid, Map<String, Object> payload) {
        String engineName = resolveEngine(payload);
        ImageGenerationEngine engine = engines.get(engineName);
        if (engine == null) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "生图服务暂不可用，请联系管理员检查配置"
            );
        }
        return engine.generate(clientUid, payload);
    }

    private String resolveEngine(Map<String, Object> payload) {
        String requested = payload == null ? "" : safe(payload.get("engine"));
        String configured = safe(settingsService.getSettings().getEngine());
        String value = StringUtils.hasText(requested) ? requested : configured;
        return normalizeEngine(value);
    }

    private static String normalizeEngine(String value) {
        String text = safe(value).toLowerCase()
                .replace('-', '_')
                .replace(' ', '_');
        if ("openai".equals(text)
                || "provider".equals(text)
                || "openai_compatible".equals(text)
                || "user".equals(text)
                || "user_openai".equals(text)
                || "user_openai_compatible".equals(text)) {
            return "openai_compatible";
        }
        if ("managed".equals(text)
                || "platform".equals(text)
                || "managed_openai".equals(text)
                || "platform_openai".equals(text)
                || "managed_openai_compatible".equals(text)
                || "platform_openai_compatible".equals(text)) {
            return "managed_openai_compatible";
        }
        if ("comfy".equals(text) || "st_comfyui".equals(text)) {
            return "st_comfy";
        }
        if ("sd_webui".equals(text) || "webui".equals(text)) {
            return "st_sd_webui";
        }
        return StringUtils.hasText(text) ? text : "openai_compatible";
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

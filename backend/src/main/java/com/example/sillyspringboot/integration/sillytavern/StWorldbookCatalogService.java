package com.example.sillyspringboot.integration.sillytavern;

import com.example.sillyspringboot.integration.sillytavern.dto.StWorldbookOptionDto;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class StWorldbookCatalogService {

    private final StAdapter stAdapter;

    public StWorldbookCatalogService(StAdapter stAdapter) {
        this.stAdapter = stAdapter;
    }

    public List<StWorldbookOptionDto> listAvailableWorldbooks() {
        LinkedHashMap<String, StWorldbookOptionDto> deduped = new LinkedHashMap<>();
        for (StWorldbookOptionDto item : stAdapter.listWorldbooks()) {
            if (item == null || !StringUtils.hasText(item.fileId())) {
                continue;
            }
            String fileId = trimToMax(item.fileId(), 128);
            if (!StringUtils.hasText(fileId)) {
                continue;
            }
            String name = trimToMax(item.name(), 128);
            deduped.putIfAbsent(fileId, new StWorldbookOptionDto(fileId, StringUtils.hasText(name) ? name : fileId));
        }
        return List.copyOf(deduped.values());
    }

    public List<String> normalizeAndValidateWorldNames(List<String> worldNames) {
        WorldbookResolution resolution = resolveWorldNames(worldNames);
        if (!resolution.missing().isEmpty()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_FAILED,
                    "worldbooks not found: " + String.join(", ", resolution.missing())
            );
        }
        return resolution.matched();
    }

    public List<String> normalizeAndFilterAvailableWorldNames(List<String> worldNames) {
        return resolveWorldNames(worldNames).matched();
    }

    public WorldbookResolution resolveWorldNames(List<String> worldNames) {
        List<String> normalized = normalizeRequestedWorldNames(worldNames);
        if (normalized.isEmpty()) {
            return new WorldbookResolution(List.of(), List.of());
        }
        Set<String> available = new LinkedHashSet<>();
        for (StWorldbookOptionDto option : listAvailableWorldbooks()) {
            if (option != null && StringUtils.hasText(option.fileId())) {
                available.add(option.fileId());
            }
        }
        List<String> matched = normalized.stream()
                .filter(available::contains)
                .toList();
        List<String> missing = normalized.stream()
                .filter(item -> !available.contains(item))
                .toList();
        return new WorldbookResolution(List.copyOf(matched), List.copyOf(missing));
    }

    public static List<String> normalizeRequestedWorldNames(List<String> worldNames) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (worldNames != null) {
            for (String item : worldNames) {
                String text = trimToMax(item, 128);
                if (StringUtils.hasText(text)) {
                    ordered.add(text);
                }
                if (ordered.size() >= 10) {
                    break;
                }
            }
        }
        return List.copyOf(ordered);
    }

    private static String trimToMax(String value, int max) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max).trim();
    }

    public record WorldbookResolution(List<String> matched, List<String> missing) {
    }
}

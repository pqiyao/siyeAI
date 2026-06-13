package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.character.entity.AppCharacter;
import com.example.sillyspringboot.character.entity.AppLorebookEntry;
import com.example.sillyspringboot.character.mapper.AppCharacterMapper;
import com.example.sillyspringboot.character.mapper.AppLorebookEntryMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/lorebook")
@AdminPermitted("content:lorebook:view")
public class AdminJiugaiLorebookController {

    private final AppLorebookEntryMapper lorebookEntryMapper;
    private final AppCharacterMapper characterMapper;

    public AdminJiugaiLorebookController(
            AppLorebookEntryMapper lorebookEntryMapper,
            AppCharacterMapper characterMapper
    ) {
        this.lorebookEntryMapper = lorebookEntryMapper;
        this.characterMapper = characterMapper;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam long characterId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        int p = Math.max(0, pageNum - 1);
        int size = Math.min(100, Math.max(1, pageSize));
        long total = lorebookEntryMapper.countByCharacterId(characterId);
        List<AppLorebookEntry> rows = lorebookEntryMapper.listPageByCharacterId(characterId, p * size, size);
        return AdminAjaxResult.table(total, rows);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        AppLorebookEntry entry = lorebookEntryMapper.findById(id);
        if (entry == null) {
            return AdminAjaxResult.error("世界书条目不存在");
        }
        Map<String, Object> r = AdminAjaxResult.ok();
        r.put("data", entry);
        return r;
    }

    @PostMapping
    @AdminPermitted("content:lorebook:edit")
    public Map<String, Object> add(@RequestBody(required = false) AppLorebookEntry body) {
        String error = validate(body);
        if (error != null) {
            return AdminAjaxResult.error(error);
        }
        normalize(body);
        lorebookEntryMapper.insert(body);
        return AdminAjaxResult.ok("新增成功");
    }

    @PutMapping
    @AdminPermitted("content:lorebook:edit")
    public Map<String, Object> update(@RequestBody(required = false) AppLorebookEntry body) {
        if (body == null || body.getId() == null) {
            return AdminAjaxResult.error("缺少 id");
        }
        if (lorebookEntryMapper.findById(body.getId()) == null) {
            return AdminAjaxResult.error("世界书条目不存在");
        }
        String error = validate(body);
        if (error != null) {
            return AdminAjaxResult.error(error);
        }
        normalize(body);
        lorebookEntryMapper.updateById(body);
        return AdminAjaxResult.ok("保存成功");
    }

    @DeleteMapping("/{ids}")
    @Transactional
    @AdminPermitted("content:lorebook:edit")
    public Map<String, Object> remove(@PathVariable String ids) {
        for (Long id : parseIds(ids)) {
            lorebookEntryMapper.deleteById(id);
        }
        return AdminAjaxResult.ok("删除成功");
    }

    @PostMapping("/batch-enabled")
    @Transactional
    @AdminPermitted("content:lorebook:edit")
    public Map<String, Object> batchEnabled(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null || !(body.get("ids") instanceof List<?> rawIds)) {
            return AdminAjaxResult.error("缺少 ids");
        }
        List<Long> ids = new ArrayList<>();
        for (Object rawId : rawIds) {
            if (rawId instanceof Number number) {
                ids.add(number.longValue());
            } else {
                try {
                    ids.add(Long.parseLong(String.valueOf(rawId).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        if (!ids.isEmpty()) {
            lorebookEntryMapper.batchEnabled(ids, enabled);
        }
        return AdminAjaxResult.ok("操作成功");
    }

    private String validate(AppLorebookEntry body) {
        if (body == null || body.getCharacterId() == null || body.getCharacterId() <= 0) {
            return "characterId 不合法";
        }
        AppCharacter character = characterMapper.findById(body.getCharacterId());
        if (character == null || character.getDeletedAt() != null) {
            return "角色不存在";
        }
        if (body.getContent() == null || body.getContent().isBlank()) {
            return "内容不能为空";
        }
        return null;
    }

    private static void normalize(AppLorebookEntry body) {
        body.setKeywordsCsv(body.getKeywordsCsv() == null ? "" : body.getKeywordsCsv().trim());
        body.setPriority(body.getPriority() == null ? 0 : body.getPriority());
        body.setConstantInjection(Boolean.TRUE.equals(body.getConstantInjection()));
        body.setScanDepth(body.getScanDepth() == null ? 4 : Math.max(1, Math.min(64, body.getScanDepth())));
        body.setEnabled(body.getEnabled() == null || body.getEnabled());
    }

    private static List<Long> parseIds(String ids) {
        List<Long> out = new ArrayList<>();
        if (ids == null || ids.isBlank()) {
            return out;
        }
        for (String token : ids.split(",")) {
            try {
                out.add(Long.parseLong(token.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }
}

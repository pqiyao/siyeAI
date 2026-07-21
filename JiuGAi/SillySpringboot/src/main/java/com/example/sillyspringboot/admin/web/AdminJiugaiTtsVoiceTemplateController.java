package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.compat.h5.web.H5UploadService;
import com.example.sillyspringboot.ops.service.TtsVoiceTemplateService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/tts-voice-template")
@AdminPermitted("commerce:entitlement:view")
public class AdminJiugaiTtsVoiceTemplateController {

    private final TtsVoiceTemplateService templateService;
    private final H5UploadService uploadService;

    public AdminJiugaiTtsVoiceTemplateController(
            TtsVoiceTemplateService templateService,
            H5UploadService uploadService
    ) {
        this.templateService = templateService;
        this.uploadService = uploadService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled
    ) {
        return AdminAjaxResult.table(templateService.listAdmin(keyword, enabled).size(), templateService.listAdmin(keyword, enabled));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        Map<String, Object> data = templateService.get(id);
        if (data == null) {
            return AdminAjaxResult.error("音色模板不存在");
        }
        return AdminAjaxResult.okData(data);
    }

    @PostMapping
    @AdminPermitted("commerce:entitlement:edit")
    public Map<String, Object> add(@RequestBody(required = false) Map<String, Object> body) {
        return AdminAjaxResult.okData(templateService.save(body));
    }

    @PutMapping
    @AdminPermitted("commerce:entitlement:edit")
    public Map<String, Object> update(@RequestBody(required = false) Map<String, Object> body) {
        return AdminAjaxResult.okData(templateService.save(body));
    }

    @DeleteMapping("/{id}")
    @AdminPermitted("commerce:entitlement:edit")
    public Map<String, Object> remove(@PathVariable long id) {
        templateService.remove(id);
        return AdminAjaxResult.ok("删除成功");
    }

    @PostMapping(value = "/upload/audio", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminPermitted("commerce:entitlement:edit")
    public Map<String, Object> uploadAudio(@RequestPart("file") MultipartFile file) {
        String url = uploadService.saveAudioAndGetUrl(file);
        Map<String, Object> result = AdminAjaxResult.ok("ok");
        result.put("fileName", url);
        return result;
    }

    @PostMapping(value = "/upload/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @AdminPermitted("commerce:entitlement:edit")
    public Map<String, Object> uploadImage(@RequestPart("file") MultipartFile file) {
        String url = uploadService.saveImageAndGetUrl(file);
        Map<String, Object> result = AdminAjaxResult.ok("ok");
        result.put("fileName", url);
        return result;
    }
}

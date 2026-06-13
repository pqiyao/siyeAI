package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.compat.h5.web.H5UploadService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/upload")
@AdminPermitted({"content:character:edit", "content:illustration:edit"})
public class AdminJiugaiUploadController {

    private final H5UploadService uploadService;

    public AdminJiugaiUploadController(H5UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadImage(@RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return AdminAjaxResult.error("文件不能为空");
        }
        String url = uploadService.saveAndGetUrl(file);
        Map<String, Object> r = AdminAjaxResult.ok("ok");
        r.put("fileName", url);
        return r;
    }
}

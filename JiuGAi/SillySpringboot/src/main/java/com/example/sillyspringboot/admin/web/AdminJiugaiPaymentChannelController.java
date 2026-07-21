package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.billing.entity.AppPaymentChannelConfig;
import com.example.sillyspringboot.billing.service.PaymentChannelConfigService;
import com.example.sillyspringboot.billing.service.StoreService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/jiugai/payment-channel")
@AdminPermitted("commerce:payment:view")
public class AdminJiugaiPaymentChannelController {

    private final StoreService storeService;
    private final PaymentChannelConfigService paymentChannelConfigService;

    public AdminJiugaiPaymentChannelController(
            StoreService storeService,
            PaymentChannelConfigService paymentChannelConfigService
    ) {
        this.storeService = storeService;
        this.paymentChannelConfigService = paymentChannelConfigService;
    }

    @GetMapping("/list")
    public Map<String, Object> list() {
        Map<String, Object> result = AdminAjaxResult.ok();
        result.put("rows", storeService.listAdminPaymentChannels());
        result.put("hint", hints());
        return result;
    }

    @PutMapping
    @AdminPermitted("commerce:payment:edit")
    public Map<String, Object> save(@RequestBody(required = false) AppPaymentChannelConfig body) {
        try {
            AppPaymentChannelConfig saved = paymentChannelConfigService.saveFromAdmin(body);
            Map<String, Object> result = AdminAjaxResult.ok("支付渠道已保存");
            result.put("data", saved);
            return result;
        } catch (BusinessException e) {
            return AdminAjaxResult.error(e.getMessage());
        }
    }

    private Map<String, Object> hints() {
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("wechat_h5", "微信 H5 需要商户号、API v3 密钥、商户证书序列号与私钥。");
        hints.put("alipay_wap", "支付宝需要 AppId、应用私钥、notify_url、return_url。");
        hints.put("telegram_star", "Telegram Stars 需要 Bot Token、Bot 用户名与 webhook secret。");
        hints.put("mock", "模拟支付默认仅测试环境可用，生产环境建议关闭。");
        return hints;
    }
}

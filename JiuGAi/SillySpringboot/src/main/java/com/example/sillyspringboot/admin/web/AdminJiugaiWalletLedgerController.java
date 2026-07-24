package com.example.sillyspringboot.admin.web;

import com.example.sillyspringboot.admin.security.AdminPermitted;
import com.example.sillyspringboot.admin.web.support.AdminAjaxResult;
import com.example.sillyspringboot.billing.service.WalletLedgerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Ǯˮֵ / ѣ
 * GET /admin/jiugai/wallet-ledger/list?pageNum=&pageSize=&keyword=&bizType=
 * bizType: PAYMENT | CHAT_CONSUME | IMAGE_CONSUME | TTS_CONSUME
 */
@RestController
@RequestMapping("/admin/jiugai/wallet-ledger")
@AdminPermitted("commerce:wallet:view")
public class AdminJiugaiWalletLedgerController {

    private final WalletLedgerService walletLedgerService;

    public AdminJiugaiWalletLedgerController(WalletLedgerService walletLedgerService) {
        this.walletLedgerService = walletLedgerService;
    }

    @GetMapping("/list")
    public Map<String, Object> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String bizType
    ) {
        return AdminAjaxResult.table(
                walletLedgerService.countAdmin(keyword, bizType),
                walletLedgerService.listAdmin(keyword, bizType, pageNum, pageSize)
        );
    }
}

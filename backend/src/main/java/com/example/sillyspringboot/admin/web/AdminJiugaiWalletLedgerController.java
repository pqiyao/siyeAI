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
 * 钱包流水查询接口。
 * GET /admin/jiugai/wallet-ledger/list?pageNum=&pageSize=&keyword=&bizType=
 * bizType 支持充值、签到、聊天，以及 IMAGE/TTS/STT/VISION 的消费和退款类型。
 * groupType 支持 REVENUE、OTHER、CONSUMPTION，用于页面内分栏和权益消耗视图。
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
            @RequestParam(required = false) String bizType,
            @RequestParam(required = false) String groupType
    ) {
        return AdminAjaxResult.table(
                walletLedgerService.countAdmin(keyword, bizType, groupType),
                walletLedgerService.listAdmin(keyword, bizType, groupType, pageNum, pageSize)
        );
    }
}

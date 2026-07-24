package com.example.sillyspringboot.billing.service;

import com.example.sillyspringboot.billing.entity.AppWalletLedger;
import com.example.sillyspringboot.billing.mapper.AppWalletLedgerMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class WalletLedgerService {

    public static final String BIZ_PAYMENT = "PAYMENT";
    public static final String BIZ_CHAT_CONSUME = "CHAT_CONSUME";
    public static final String BIZ_IMAGE_CONSUME = "IMAGE_CONSUME";
    public static final String BIZ_IMAGE_REFUND = "IMAGE_REFUND";
    public static final String BIZ_TTS_CONSUME = "TTS_CONSUME";
    public static final String BIZ_TTS_REFUND = "TTS_REFUND";
    public static final String BIZ_STT_CONSUME = "STT_CONSUME";
    public static final String BIZ_STT_REFUND = "STT_REFUND";
    public static final String BIZ_CHECKIN = "CHECKIN";

    private final AppWalletLedgerMapper walletLedgerMapper;
    private final AppH5UserProfileExtMapper profileExtMapper;

    public WalletLedgerService(
            AppWalletLedgerMapper walletLedgerMapper,
            AppH5UserProfileExtMapper profileExtMapper
    ) {
        this.walletLedgerMapper = walletLedgerMapper;
        this.profileExtMapper = profileExtMapper;
    }

    /**
     * Payment credit ledger. Idempotency key = PAYMENT:{orderNo}.
     *
     * @return true if a new ledger row was written; false if already existed
     */
    @Transactional
    public boolean insertPaymentCredit(long userId, String orderNo, int deltaScore, int deltaGoldCoin, String note) {
        if (deltaScore < 0 || deltaGoldCoin < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "支付入账金额不能为负数");
        }
        String safeOrderNo = trimToNull(orderNo);
        if (safeOrderNo == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "\u8ba2\u5355\u53f7\u4e0d\u80fd\u4e3a\u7a7a");
        }
        String idempotencyKey = BIZ_PAYMENT + ":" + safeOrderNo;
        if (walletLedgerMapper.findByIdempotencyKey(idempotencyKey) != null) {
            return false;
        }
        try {
            int inserted = walletLedgerMapper.insertFull(
                    userId,
                    BIZ_PAYMENT,
                    safeOrderNo,
                    safeOrderNo,
                    idempotencyKey,
                    deltaScore,
                    deltaGoldCoin,
                    note == null ? "" : note
            );
            if (inserted != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "支付流水写入失败");
            }
            return true;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    /**
     * Check-in / campaign credit. Idempotency key = CHECKIN:{bizRef}.
     *
     * @return true if a new ledger row was written
     */
    @Transactional
    public boolean insertCheckinCredit(long userId, String bizRef, int deltaScore, int deltaGoldCoin, String note) {
        if (deltaScore < 0 || deltaGoldCoin < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "check-in credit cannot be negative");
        }
        if (deltaScore == 0 && deltaGoldCoin == 0) {
            return false;
        }
        String safeBizRef = trimToNull(bizRef);
        if (safeBizRef == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "check-in bizRef required");
        }
        String idempotencyKey = BIZ_CHECKIN + ":" + safeBizRef;
        if (walletLedgerMapper.findByIdempotencyKey(idempotencyKey) != null) {
            return false;
        }
        if (profileExtMapper.creditWallet(userId, deltaScore, deltaGoldCoin) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "check-in wallet credit failed");
        }
        try {
            int inserted = walletLedgerMapper.insertFull(
                    userId,
                    BIZ_CHECKIN,
                    null,
                    safeBizRef,
                    idempotencyKey,
                    deltaScore,
                    deltaGoldCoin,
                    note == null ? "" : note
            );
            if (inserted != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "check-in ledger write failed");
            }
            return true;
        } catch (DuplicateKeyException ignored) {
            if (profileExtMapper.deductWallet(userId, deltaScore, deltaGoldCoin) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "check-in duplicate credit compensate failed");
            }
            return false;
        }
    }

    /**
     * Atomically deduct wallet and write consume ledger.
     * Idempotency key = {bizType}:{bizRef}.
     * On concurrent duplicate after deduct, refund immediately to avoid double-charge.
     */
    @Transactional
    public boolean consumeDiamonds(
            long userId,
            int scoreCost,
            int goldCost,
            String bizType,
            String bizRef,
            String note
    ) {
        int safeScore = Math.max(0, scoreCost);
        int safeGold = Math.max(0, goldCost);
        if (safeScore == 0 && safeGold == 0) {
            return false;
        }
        String safeBizType = normalizeBizType(bizType);
        String safeBizRef = trimToNull(bizRef);
        if (safeBizRef == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "\u6d88\u8017\u4e1a\u52a1\u5f15\u7528\u4e0d\u80fd\u4e3a\u7a7a");
        }
        String idempotencyKey = safeBizType + ":" + safeBizRef;
        if (walletLedgerMapper.findByIdempotencyKey(idempotencyKey) != null) {
            return false;
        }

        int updated = profileExtMapper.deductWallet(userId, safeScore, safeGold);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "\u514d\u8d39\u6b21\u6570\u5df2\u7528\u5b8c\uff0c\u94bb\u77f3\u4e0d\u8db3\uff0c\u8bf7\u5145\u503c");
        }
        try {
            int inserted = walletLedgerMapper.insertFull(
                    userId,
                    safeBizType,
                    null,
                    safeBizRef,
                    idempotencyKey,
                    -safeScore,
                    -safeGold,
                    note == null ? "" : note
            );
            if (inserted != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "钱包消费流水写入失败");
            }
            return true;
        } catch (DuplicateKeyException ignored) {
            // Another concurrent request already completed this bizRef. Refund this thread's deduct.
            if (profileExtMapper.creditWallet(userId, safeScore, safeGold) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "钱包重复消费补偿失败");
            }
            return false;
        }
    }

    @Transactional(readOnly = true)
    public boolean hasLedgerEntry(String bizType, String bizRef) {
        String safeBizType = normalizeBizType(bizType);
        String safeBizRef = trimToNull(bizRef);
        if (safeBizRef == null) {
            return false;
        }
        return walletLedgerMapper.findByIdempotencyKey(safeBizType + ":" + safeBizRef) != null;
    }

    /**
     * Refund a previous consume. Idempotency key = {bizType}:{bizRef}.
     * Credits wallet and writes a positive ledger row.
     */
    @Transactional
    public void refundConsume(
            long userId,
            int scoreAmount,
            int goldAmount,
            String bizType,
            String bizRef,
            String note
    ) {
        int safeScore = Math.max(0, scoreAmount);
        int safeGold = Math.max(0, goldAmount);
        if (safeScore == 0 && safeGold == 0) {
            return;
        }
        String safeBizType = normalizeBizType(bizType);
        String safeBizRef = trimToNull(bizRef);
        if (safeBizRef == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "消耗业务引用不能为空");
        }
        String idempotencyKey = safeBizType + ":" + safeBizRef;
        if (walletLedgerMapper.findByIdempotencyKey(idempotencyKey) != null) {
            return;
        }
        if (profileExtMapper.creditWallet(userId, safeScore, safeGold) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "退款账户不存在或余额回补失败");
        }
        try {
            int inserted = walletLedgerMapper.insertFull(
                    userId,
                    safeBizType,
                    null,
                    safeBizRef,
                    idempotencyKey,
                    safeScore,
                    safeGold,
                    note == null ? "" : note
            );
            if (inserted != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "钱包退款流水写入失败");
            }
        } catch (DuplicateKeyException ignored) {
            // Concurrent refund already recorded; reverse this thread's credit to keep balance correct.
            if (profileExtMapper.deductWallet(userId, safeScore, safeGold) != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "钱包重复退款冲正失败");
            }
        }
    }

    @Transactional(readOnly = true)
    public long countAdmin(String keyword, String bizType) {
        return walletLedgerMapper.countAdminList(trimToNull(keyword), normalizeBizTypeAllowBlank(bizType));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdmin(String keyword, String bizType, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        return walletLedgerMapper.listAdminPage(
                        trimToNull(keyword),
                        normalizeBizTypeAllowBlank(bizType),
                        (safePage - 1) * safeSize,
                        safeSize
                ).stream()
                .map(this::toAdminMap)
                .toList();
    }

    private Map<String, Object> toAdminMap(AppWalletLedger row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.getId());
        data.put("userId", row.getUserId());
        data.put("bizType", blank(row.getBizType()));
        data.put("orderNo", blank(row.getOrderNo()));
        data.put("bizRef", blank(row.getBizRef()));
        data.put("idempotencyKey", blank(row.getIdempotencyKey()));
        data.put("deltaScore", row.getDeltaScore() == null ? 0 : row.getDeltaScore());
        data.put("deltaGoldCoin", row.getDeltaGoldCoin() == null ? 0 : row.getDeltaGoldCoin());
        data.put("note", blank(row.getNote()));
        data.put("createdAt", row.getCreatedAt());
        return data;
    }

    private static String normalizeBizType(String bizType) {
        String value = normalizeBizTypeAllowBlank(bizType);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "\u4e1a\u52a1\u7c7b\u578b\u4e0d\u80fd\u4e3a\u7a7a");
        }
        return value;
    }

    private static String normalizeBizTypeAllowBlank(String bizType) {
        if (bizType == null || bizType.isBlank()) {
            return null;
        }
        return bizType.trim().toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }
}

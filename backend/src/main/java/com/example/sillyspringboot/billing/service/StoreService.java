package com.example.sillyspringboot.billing.service;

import com.example.sillyspringboot.auth.entity.AppUser;
import com.example.sillyspringboot.auth.mapper.AppUserMapper;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.billing.entity.AppPaymentOrder;
import com.example.sillyspringboot.billing.entity.AppStoreProduct;
import com.example.sillyspringboot.billing.mapper.AppPaymentOrderMapper;
import com.example.sillyspringboot.billing.mapper.AppStoreProductMapper;
import com.example.sillyspringboot.billing.service.provider.StorePaymentContext;
import com.example.sillyspringboot.billing.service.provider.StorePaymentProvider;
import com.example.sillyspringboot.billing.service.provider.StorePaymentProviderRegistry;
import com.example.sillyspringboot.compat.h5.entity.AppH5UserProfileExt;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ClientUidMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.ops.service.EntitlementAuditLogService;
import com.example.sillyspringboot.ops.service.EntitlementPolicyService;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.shared.error.BusinessException;
import com.example.sillyspringboot.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class StoreService {

    private static final SecureRandom ORDER_RANDOM = new SecureRandom();
    private static final long ORDER_EXPIRATION_MINUTES = 2L;

    private final H5ClientUidAuthService h5Auth;
    private final AppTokenService tokenService;
    private final AppUserMapper userMapper;
    private final AppH5ClientUidMapper h5ClientUidMapper;
    private final AppH5UserProfileExtMapper profileExtMapper;
    private final AppStoreProductMapper productMapper;
    private final AppPaymentOrderMapper orderMapper;
    private final WalletLedgerService walletLedgerService;
    private final H5EntitlementService h5EntitlementService;
    private final EntitlementAuditLogService entitlementAuditLogService;
    private final EntitlementPolicyService entitlementPolicyService;
    private final StorePaymentProviderRegistry paymentProviderRegistry;

    public StoreService(
            H5ClientUidAuthService h5Auth,
            AppTokenService tokenService,
            AppUserMapper userMapper,
            AppH5ClientUidMapper h5ClientUidMapper,
            AppH5UserProfileExtMapper profileExtMapper,
            AppStoreProductMapper productMapper,
            AppPaymentOrderMapper orderMapper,
            WalletLedgerService walletLedgerService,
            H5EntitlementService h5EntitlementService,
            EntitlementAuditLogService entitlementAuditLogService,
            EntitlementPolicyService entitlementPolicyService,
            StorePaymentProviderRegistry paymentProviderRegistry
    ) {
        this.h5Auth = h5Auth;
        this.tokenService = tokenService;
        this.userMapper = userMapper;
        this.h5ClientUidMapper = h5ClientUidMapper;
        this.profileExtMapper = profileExtMapper;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.walletLedgerService = walletLedgerService;
        this.h5EntitlementService = h5EntitlementService;
        this.entitlementAuditLogService = entitlementAuditLogService;
        this.entitlementPolicyService = entitlementPolicyService;
        this.paymentProviderRegistry = paymentProviderRegistry;
    }

    @Transactional
    public Map<String, Object> overview(String clientUid) {
        AppUser user = resolveUser(clientUid);
        AppH5UserProfileExt ext = ensureProfileExt(user);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profile", toProfileMap(user, ext));
        data.put("products", Map.of(
                "coin", toProductMaps(productMapper.listEnabled("COIN")),
                "vip", toProductMaps(productMapper.listEnabled("VIP"))
        ));
        data.put("orders", toOrderMaps(orderMapper.listByUserId(user.getId(), 10)));
        data.put("channels", clientFacingChannels());
        return data;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listProducts(String productType) {
        return toProductMaps(productMapper.listEnabled(normalizeProductTypeAllowBlank(productType)));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listOrders(String clientUid, int limit) {
        AppUser user = resolveUser(clientUid);
        int safeLimit = Math.max(1, Math.min(50, limit));
        return toOrderMaps(orderMapper.listByUserId(user.getId(), safeLimit));
    }

    @Transactional
    public Map<String, Object> removeUnpaidOrder(String clientUid, String orderNo) {
        AppUser user = resolveUser(clientUid);
        String safeOrderNo = orderNo == null ? "" : orderNo.trim();
        if (safeOrderNo.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "订单号不能为空");
        }

        AppPaymentOrder order = orderMapper.findByOrderNoAndUserId(safeOrderNo, user.getId());
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if ("PAID".equalsIgnoreCase(order.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "已支付订单不能删除");
        }

        int updated = orderMapper.hideUnpaidByOrderNoAndUserId(safeOrderNo, user.getId());
        if (updated != 1) {
            AppPaymentOrder current = orderMapper.findByOrderNoAndUserId(safeOrderNo, user.getId());
            if (current != null && "PAID".equalsIgnoreCase(current.getStatus())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "订单已支付，不能删除");
            }
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "订单状态已变化，请刷新后重试");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("removed", true);
        data.put("orderNo", safeOrderNo);
        return data;
    }

    @Transactional
    public Map<String, Object> createOrder(String clientUid, String productCode, String paymentChannel, StorePaymentContext paymentContext) {
        AppUser user = resolveUser(clientUid);
        AppH5UserProfileExt ext = ensureProfileExt(user);

        String code = productCode == null ? "" : productCode.trim();
        if (code.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "请选择商品");
        }

        AppStoreProduct product = productMapper.findByCode(code);
        if (product == null || !Boolean.TRUE.equals(product.getEnabled())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在或已下架");
        }

        String normalizedChannel = normalizeChannel(paymentChannel);
        StorePaymentProvider provider = paymentProviderRegistry.resolveRequired(normalizedChannel);

        AppPaymentOrder order = new AppPaymentOrder();
        order.setOrderNo(nextOrderNo());
        order.setUserId(user.getId());
        order.setProductId(product.getId());
        order.setProductCode(product.getCode());
        order.setProductName(product.getName());
        order.setProductType(product.getProductType());
        order.setAmountCents(nvl(product.getPriceCents()));
        order.setScoreAmount(nvl(product.getScoreAmount()));
        order.setGoldCoinAmount(nvl(product.getGoldCoinAmount()));
        order.setVipType(nvl(product.getVipType()));
        order.setVipDays(nvl(product.getVipDays()));
        order.setPaymentChannel(normalizedChannel);
        order.setStatus("PENDING");
        order.setExpiresAt(LocalDateTime.now().plusMinutes(ORDER_EXPIRATION_MINUTES));
        orderMapper.insert(order);

        AppPaymentOrder saved = orderMapper.findByOrderNoAndUserId(order.getOrderNo(), user.getId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order", toOrderMap(saved));
        data.put("payment", provider.createPayment(normalizedChannel, saved, product, user, ext, safeContext(paymentContext)));
        data.put("channels", clientFacingChannels());
        return data;
    }

    @Transactional
    public Map<String, Object> startOrderPayment(String clientUid, String orderNo, StorePaymentContext paymentContext) {
        AppUser user = resolveUser(clientUid);
        AppH5UserProfileExt ext = ensureProfileExt(user);
        if (orderNo == null || orderNo.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "订单号不能为空");
        }

        AppPaymentOrder order = orderMapper.findByOrderNoAndUserId(orderNo.trim(), user.getId());
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if ("PAID".equalsIgnoreCase(order.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "订单已支付，请勿重复发起支付");
        }
        orderMapper.closeExpiredPendingById(order.getId());
        order = orderMapper.findByOrderNoAndUserId(orderNo.trim(), user.getId());
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if ("PAID".equalsIgnoreCase(order.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "订单已支付，请勿重复发起支付");
        }
        if ("CLOSED".equalsIgnoreCase(order.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "订单已关闭，请重新下单");
        }

        AppStoreProduct product = order.getProductId() == null ? null : productMapper.findById(order.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单商品不存在");
        }

        StorePaymentProvider provider = paymentProviderRegistry.resolveRequired(normalizeChannel(order.getPaymentChannel()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order", toOrderMap(order));
        data.put("payment", provider.createPayment(order.getPaymentChannel(), order, product, user, ext, safeContext(paymentContext)));
        return data;
    }

    @Transactional
    public Map<String, Object> mockPay(String clientUid, String orderNo) {
        AppUser user = resolveUser(clientUid);
        if (orderNo == null || orderNo.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "订单号不能为空");
        }

        AppPaymentOrder order = orderMapper.findByOrderNoAndUserId(orderNo.trim(), user.getId());
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        orderMapper.closeExpiredPendingById(order.getId());
        order = orderMapper.findByOrderNoAndUserId(orderNo.trim(), user.getId());
        if (order == null || "CLOSED".equalsIgnoreCase(order.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "订单已关闭，请重新下单");
        }

        StorePaymentProvider provider = paymentProviderRegistry.resolveRequired(normalizeChannel(order.getPaymentChannel()));
        if (!provider.supportsManualSettlement(order.getPaymentChannel())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前支付渠道不支持模拟支付");
        }

        if (!"PAID".equalsIgnoreCase(order.getStatus())) {
            int updated = orderMapper.markPaid(order.getId(), null, order.getAmountCents(), null);
            if (updated == 1) {
                order = orderMapper.findByOrderNoAndUserId(orderNo.trim(), user.getId());
                applyOrderBenefits(user, order);
            } else {
                order = orderMapper.findByOrderNoAndUserId(orderNo.trim(), user.getId());
            }
        }

        AppH5UserProfileExt ext = ensureProfileExt(user);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order", toOrderMap(order));
        data.put("profile", toProfileMap(user, ext));
        data.put("payment", provider.manualSettlementResult(order.getPaymentChannel(), order));
        return data;
    }

    @Transactional
    public Map<String, Object> confirmProviderPaid(String orderNo, String expectedChannel) {
        return confirmProviderPaid(orderNo, expectedChannel, null, null, null);
    }

    @Transactional
    public Map<String, Object> confirmProviderPaid(
            String orderNo,
            String expectedChannel,
            Integer paidAmountCents,
            String providerTradeNo,
            String notifyPayloadHash
    ) {
        String safeOrderNo = trimToNull(orderNo);
        if (safeOrderNo == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "订单号不能为空");
        }

        AppPaymentOrder order = orderMapper.findByOrderNo(safeOrderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单不存在");
        }

        String expected = normalizeChannel(expectedChannel);
        String actual = normalizeChannel(order.getPaymentChannel());
        if (expected != null && !expected.isBlank() && !expected.equals(actual)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "订单支付渠道不匹配");
        }

        if (paidAmountCents != null && paidAmountCents.intValue() != nvl(order.getAmountCents())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "支付金额与订单金额不一致");
        }

        AppUser user = userMapper.findById(order.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "订单用户不存在");
        }

        if (!"PAID".equalsIgnoreCase(order.getStatus())) {
            int updated = orderMapper.markPaid(
                    order.getId(),
                    trimToNull(providerTradeNo),
                    paidAmountCents != null ? paidAmountCents : order.getAmountCents(),
                    trimToNull(notifyPayloadHash)
            );
            if (updated == 1) {
                order = orderMapper.findByOrderNo(safeOrderNo);
                applyOrderBenefits(user, order);
            } else {
                order = orderMapper.findByOrderNo(safeOrderNo);
            }
        }

        AppH5UserProfileExt ext = ensureProfileExt(user);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order", toOrderMap(order));
        data.put("profile", toProfileMap(user, ext));
        return data;
    }

    @Transactional(readOnly = true)
    public long countAdminProducts(String keyword, String productType) {
        return productMapper.countAdminList(trimToNull(keyword), normalizeProductTypeAllowBlank(productType));
    }

    @Transactional(readOnly = true)
    public List<AppStoreProduct> listAdminProducts(String keyword, String productType, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        return productMapper.listAdminPage(
                trimToNull(keyword),
                normalizeProductTypeAllowBlank(productType),
                (safePage - 1) * safeSize,
                safeSize
        );
    }

    @Transactional(readOnly = true)
    public AppStoreProduct getProduct(long id) {
        return productMapper.findById(id);
    }

    @Transactional
    public AppStoreProduct saveProduct(AppStoreProduct body) {
        if (body == null || body.getCode() == null || body.getCode().isBlank() || body.getName() == null || body.getName().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "商品编码和名称不能为空");
        }
        body.setCode(body.getCode().trim());
        body.setName(body.getName().trim());
        body.setProductType(normalizeProductType(body.getProductType()));
        body.setPriceCents(nvl(body.getPriceCents()));
        body.setScoreAmount(nvl(body.getScoreAmount()));
        body.setGoldCoinAmount(nvl(body.getGoldCoinAmount()));
        body.setVipType(nvl(body.getVipType()));
        body.setVipDays(nvl(body.getVipDays()));
        body.setSortOrder(nvl(body.getSortOrder()));
        body.setEnabled(body.getEnabled() == null || body.getEnabled());
        validateProductBenefits(body);
        if (body.getId() == null) {
            productMapper.insert(body);
            return productMapper.findById(body.getId());
        }
        productMapper.updateById(body);
        return productMapper.findById(body.getId());
    }

    @Transactional(readOnly = true)
    public long countAdminOrders(String keyword, String status) {
        return orderMapper.countAdminList(trimToNull(keyword), normalizeStatus(status));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdminOrders(String keyword, String status, int pageNum, int pageSize) {
        int safePage = Math.max(1, pageNum);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        return orderMapper.listAdminPage(
                        trimToNull(keyword),
                        normalizeStatus(status),
                        (safePage - 1) * safeSize,
                        safeSize
                ).stream()
                .map(this::toAdminOrderMap)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAdminOrder(String orderNo) {
        AppPaymentOrder order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            return null;
        }
        return toAdminOrderMap(order);
    }

    private Map<String, Object> toAdminOrderMap(AppPaymentOrder order) {
        Map<String, Object> data = new LinkedHashMap<>(toOrderMap(order));
        String clientUid = h5ClientUidMapper.findAnyClientUidByUserId(order.getUserId());
        AppUser user = userMapper.findById(order.getUserId());
        data.put("clientUid", clientUid == null ? "" : clientUid);
        data.put("username", user == null ? "" : fallbackUsername(user));
        return data;
    }

    private void applyOrderBenefits(AppUser user, AppPaymentOrder order) {
        if (order == null) {
            return;
        }
        ensureProfileExt(user);
        AppH5UserProfileExt ext = profileExtMapper.findByUserIdForUpdate(user.getId());
        if (ext == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "支付权益账户不存在，请人工核对订单");
        }
        Map<String, Object> beforeProfile = toProfileMap(user, ext);

        int scoreAmount = nvl(order.getScoreAmount());
        int goldCoinAmount = nvl(order.getGoldCoinAmount());
        int vipType = nvl(order.getVipType());
        int vipDays = nvl(order.getVipDays());
        if (scoreAmount < 0 || goldCoinAmount < 0 || vipType < 0 || vipDays < 0) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "支付订单权益数量非法，请人工核对订单");
        }
        if ((vipType > 0) != (vipDays > 0)
                || (scoreAmount == 0 && goldCoinAmount == 0 && vipType == 0)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "支付订单权益配置不完整，请人工核对订单");
        }
        boolean ledgerInserted = walletLedgerService.insertPaymentCredit(
                user.getId(),
                order.getOrderNo(),
                scoreAmount,
                goldCoinAmount,
                "支付到账：" + blank(order.getProductName())
        );
        if (!ledgerInserted) {
            throw new BusinessException(ErrorCode.CONFLICT, "支付流水已存在，订单结算已停止，请人工核对");
        }
        if ((scoreAmount > 0 || goldCoinAmount > 0)
                && profileExtMapper.creditWallet(user.getId(), scoreAmount, goldCoinAmount) != 1) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "支付余额入账失败，请人工核对订单");
        }

        if (vipType > 0) {
            LocalDateTime base = ext.getVipExpiresAt();
            LocalDateTime now = LocalDateTime.now();
            if (base == null || base.isBefore(now)) {
                base = now;
            }
            ext.setVipType(Math.max(nvl(ext.getVipType()), vipType));
            ext.setVipExpiresAt(base.plusDays(vipDays));
            entitlementPolicyService.refreshEffectiveQuota(ext);
            int membershipUpdated = profileExtMapper.updateMembershipAndEffectiveQuotas(
                    user.getId(),
                    nvl(ext.getVipType()),
                    ext.getVipExpiresAt(),
                    nvl(ext.getDailyChatQuota()),
                    ext.getChatQuotaOverride(),
                    nvl(ext.getDailyImageQuota()),
                    ext.getImageQuotaOverride()
            );
            if (membershipUpdated != 1) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "支付会员权益入账失败，请人工核对订单");
            }
        }

        AppH5UserProfileExt applied = profileExtMapper.findByUserId(user.getId());
        if (applied == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "支付权益入账后无法读取账户，请人工核对订单");
        }
        Map<String, Object> afterProfile = toProfileMap(user, applied);
        entitlementAuditLogService.recordPaymentApplied(
                user.getId(),
                order.getOrderNo(),
                beforeProfile,
                afterProfile,
                toOrderMap(order)
        );
    }

    private static void validateProductBenefits(AppStoreProduct product) {
        if (product.getPriceCents() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "商品价格必须大于 0");
        }
        if (product.getScoreAmount() < 0
                || product.getGoldCoinAmount() < 0
                || product.getVipType() < 0
                || product.getVipDays() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "商品权益数量不能为负数");
        }
        if ("COIN".equals(product.getProductType())
                && product.getScoreAmount() == 0
                && product.getGoldCoinAmount() == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "钻石商品必须提供钻石或金币权益");
        }
        if ("VIP".equals(product.getProductType())
                && (product.getVipType() <= 0 || product.getVipDays() <= 0)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "会员商品必须提供有效的会员等级和天数");
        }
    }

    private AppUser resolveUser(String clientUid) {
        String token = h5Auth.requireAuthenticatedTokenForClientUid(clientUid);
        return tokenService.validateAndLoadUser(token);
    }

    private AppH5UserProfileExt ensureProfileExt(AppUser user) {
        return h5EntitlementService.ensureProfileExt(user);
    }

    private List<Map<String, Object>> toProductMaps(List<AppStoreProduct> list) {
        return list.stream().map(this::toProductMap).toList();
    }

    private Map<String, Object> toProductMap(AppStoreProduct row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.getId());
        data.put("code", row.getCode());
        data.put("name", row.getName());
        data.put("subtitle", blank(row.getSubtitle()));
        data.put("productType", row.getProductType());
        data.put("priceCents", nvl(row.getPriceCents()));
        data.put("priceYuan", centsToYuan(nvl(row.getPriceCents())));
        data.put("scoreAmount", nvl(row.getScoreAmount()));
        data.put("goldCoinAmount", nvl(row.getGoldCoinAmount()));
        data.put("vipType", nvl(row.getVipType()));
        data.put("vipDays", nvl(row.getVipDays()));
        data.put("tagLabel", blank(row.getTagLabel()));
        data.put("badgeLabel", blank(row.getBadgeLabel()));
        data.put("enabled", Boolean.TRUE.equals(row.getEnabled()));
        data.put("sortOrder", nvl(row.getSortOrder()));
        return data;
    }

    private List<Map<String, Object>> toOrderMaps(List<AppPaymentOrder> list) {
        return list.stream().map(this::toOrderMap).toList();
    }

    private Map<String, Object> toOrderMap(AppPaymentOrder row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", row.getId());
        data.put("orderNo", row.getOrderNo());
        data.put("userId", row.getUserId());
        data.put("productId", row.getProductId());
        data.put("productCode", row.getProductCode());
        data.put("productName", row.getProductName());
        data.put("productType", row.getProductType());
        data.put("amountCents", nvl(row.getAmountCents()));
        data.put("amountYuan", centsToYuan(nvl(row.getAmountCents())));
        data.put("scoreAmount", nvl(row.getScoreAmount()));
        data.put("goldCoinAmount", nvl(row.getGoldCoinAmount()));
        data.put("vipType", nvl(row.getVipType()));
        data.put("vipDays", nvl(row.getVipDays()));
        data.put("paymentChannel", blank(row.getPaymentChannel()));
        data.put("status", blank(row.getStatus()));
        data.put("statusLabel", statusLabel(row.getStatus()));
        data.put("expiresAt", row.getExpiresAt());
        data.put("createdAt", row.getCreatedAt());
        data.put("paidAt", row.getPaidAt());
        return data;
    }

    private Map<String, Object> toProfileMap(AppUser user, AppH5UserProfileExt ext) {
        Map<String, Object> data = new LinkedHashMap<>();
        boolean vipActive = ext.getVipType() != null
                && ext.getVipType() > 0
                && ext.getVipExpiresAt() != null
                && ext.getVipExpiresAt().isAfter(LocalDateTime.now());
        String nickname = ext.getNickname();
        if (nickname == null || nickname.isBlank()) {
            nickname = fallbackUsername(user);
        }

        data.put("userId", user.getId());
        data.put("nickname", blank(nickname));
        data.put("avatar", blank(ext.getAvatar()));
        data.put("bio", blank(ext.getBio()));
        data.put("vipType", nvl(ext.getVipType()));
        data.put("vipActive", vipActive);
        data.put("vipName", vipName(ext.getVipType(), vipActive));
        data.put("vipExpiresAt", ext.getVipExpiresAt());
        data.put("score", nvl(ext.getScore()));
        data.put("goldCoin", nvl(ext.getGoldCoin()));
        int chatBonus = nvl(ext.getDailyChatBonus());
        int imageBonus = nvl(ext.getDailyImageBonus());
        data.put("dailyChatBonus", chatBonus);
        data.put("dailyImageBonus", imageBonus);
        data.put("dailyChatQuota", nvl(ext.getDailyChatQuota()));
        data.put("dailyChatUsed", nvl(ext.getDailyChatUsed()));
        data.put("dailyChatRemaining", Math.max(0, nvl(ext.getDailyChatQuota()) + chatBonus - nvl(ext.getDailyChatUsed())));
        data.put("dailyImageQuota", nvl(ext.getDailyImageQuota()));
        data.put("dailyImageUsed", nvl(ext.getDailyImageUsed()));
        data.put("dailyImageRemaining", Math.max(0, nvl(ext.getDailyImageQuota()) + imageBonus - nvl(ext.getDailyImageUsed())));
        data.put("chatQuotaOverride", ext.getChatQuotaOverride());
        data.put("imageQuotaOverride", ext.getImageQuotaOverride());
        data.put("status", blank(ext.getStatus()));
        data.put("accountLabel", fallbackUsername(user));
        data.put("telegramReady", user.getTelegramUserId() != null && user.getTelegramUserId() < 9_000_000_000L);
        return data;
    }

    private static String vipName(Integer vipType, boolean vipActive) {
        if (!vipActive || nvl(vipType) <= 0) {
            return "普通用户";
        }
        return nvl(vipType) >= 2 ? "SVIP 会员" : "VIP 会员";
    }

    private static String normalizeProductType(String productType) {
        String normalized = normalizeProductTypeAllowBlank(productType);
        if (normalized == null || normalized.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "商品类型不正");
        }
        return normalized;
    }

    private static String normalizeProductTypeAllowBlank(String productType) {
        if (productType == null || productType.isBlank()) {
            return null;
        }
        String value = productType.trim().toUpperCase(Locale.ROOT);
        if ("COIN".equals(value) || "VIP".equals(value)) {
            return value;
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "商品类型不正");
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "PENDING", "PAID", "CLOSED" -> value;
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED, "订单状态不正确");
        };
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listAdminPaymentChannels() {
        return paymentProviderRegistry.describeChannels();
    }

    public List<Map<String, Object>> clientFacingChannels() {
        return paymentProviderRegistry.describeChannels().stream()
                .filter(item -> Boolean.TRUE.equals(item.get("clientVisible")))
                .toList();
    }

    private StorePaymentContext safeContext(StorePaymentContext paymentContext) {
        return paymentContext == null ? StorePaymentContext.empty() : paymentContext;
    }

    private String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return paymentDefaultChannel();
        }
        return channel.trim().toLowerCase(Locale.ROOT);
    }

    private String paymentDefaultChannel() {
        return clientFacingChannels().stream()
                .filter(item -> Boolean.TRUE.equals(item.get("enabled")))
                .map(item -> String.valueOf(item.get("code")))
                .findFirst()
                .orElse("mock_wechat");
    }

    private static String nextOrderNo() {
        byte[] bytes = new byte[10];
        ORDER_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(26);
        sb.append("SP");
        for (byte b : bytes) {
            sb.append(String.format(Locale.ROOT, "%02X", b));
        }
        sb.append(UUID.randomUUID().toString().replace("-", ""), 0, 4);
        return sb.toString().toUpperCase(Locale.ROOT);
    }

    private static String fallbackUsername(AppUser user) {
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
            return user.getFirstName();
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return "用户" + user.getId();
    }

    private static String statusLabel(String status) {
        if ("PAID".equalsIgnoreCase(status)) {
            return "已支付";
        }
        if ("CLOSED".equalsIgnoreCase(status)) {
            return "已关闭";
        }
        return "待支付";
    }

    private static String centsToYuan(int cents) {
        return String.format(Locale.ROOT, "%.2f", cents / 100.0d);
    }

    private static int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

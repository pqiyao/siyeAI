package com.example.sillyspringboot.billing.service;

import com.example.sillyspringboot.auth.mapper.AppUserMapper;
import com.example.sillyspringboot.auth.token.AppTokenService;
import com.example.sillyspringboot.billing.entity.AppStoreProduct;
import com.example.sillyspringboot.billing.mapper.AppPaymentOrderMapper;
import com.example.sillyspringboot.billing.mapper.AppStoreProductMapper;
import com.example.sillyspringboot.billing.service.provider.StorePaymentProviderRegistry;
import com.example.sillyspringboot.compat.h5.mapper.AppH5ClientUidMapper;
import com.example.sillyspringboot.compat.h5.mapper.AppH5UserProfileExtMapper;
import com.example.sillyspringboot.compat.h5.service.H5ClientUidAuthService;
import com.example.sillyspringboot.ops.service.EntitlementAuditLogService;
import com.example.sillyspringboot.ops.service.EntitlementPolicyService;
import com.example.sillyspringboot.ops.service.H5EntitlementService;
import com.example.sillyspringboot.shared.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StoreServiceProductValidationTest {

    @Mock H5ClientUidAuthService h5Auth;
    @Mock AppTokenService tokenService;
    @Mock AppUserMapper userMapper;
    @Mock AppH5ClientUidMapper h5ClientUidMapper;
    @Mock AppH5UserProfileExtMapper profileExtMapper;
    @Mock AppStoreProductMapper productMapper;
    @Mock AppPaymentOrderMapper orderMapper;
    @Mock WalletLedgerService walletLedgerService;
    @Mock H5EntitlementService h5EntitlementService;
    @Mock EntitlementAuditLogService entitlementAuditLogService;
    @Mock EntitlementPolicyService entitlementPolicyService;
    @Mock StorePaymentProviderRegistry paymentProviderRegistry;

    @InjectMocks
    StoreService storeService;

    @Test
    void rejectsNonPositivePrice() {
        AppStoreProduct product = coinProduct();
        product.setPriceCents(0);

        assertValidationFailure(product, "商品价格必须大于 0");
    }

    @Test
    void rejectsNegativeBenefitValues() {
        AppStoreProduct product = coinProduct();
        product.setGoldCoinAmount(-1);

        assertValidationFailure(product, "商品权益数量不能为负数");
    }

    @Test
    void rejectsCoinProductWithoutWalletBenefits() {
        AppStoreProduct product = coinProduct();
        product.setScoreAmount(0);
        product.setGoldCoinAmount(0);

        assertValidationFailure(product, "钻石商品必须提供钻石或金币权益");
    }

    @Test
    void rejectsVipProductWithoutMembershipDuration() {
        AppStoreProduct product = vipProduct();
        product.setVipDays(0);

        assertValidationFailure(product, "会员商品必须提供有效的会员等级和天数");
    }

    @Test
    void acceptsExistingVipBundleShapeWithWalletBonus() {
        AppStoreProduct product = vipProduct();
        doAnswer(invocation -> {
            invocation.<AppStoreProduct>getArgument(0).setId(1L);
            return null;
        }).when(productMapper).insert(product);

        storeService.saveProduct(product);

        verify(productMapper).insert(product);
    }

    private void assertValidationFailure(AppStoreProduct product, String message) {
        assertThatThrownBy(() -> storeService.saveProduct(product))
                .isInstanceOf(BusinessException.class)
                .hasMessage(message);
        verify(productMapper, never()).insert(product);
    }

    private static AppStoreProduct coinProduct() {
        AppStoreProduct product = baseProduct("COIN");
        product.setScoreAmount(120);
        product.setGoldCoinAmount(1200);
        return product;
    }

    private static AppStoreProduct vipProduct() {
        AppStoreProduct product = baseProduct("VIP");
        product.setScoreAmount(280);
        product.setGoldCoinAmount(2800);
        product.setVipType(1);
        product.setVipDays(7);
        return product;
    }

    private static AppStoreProduct baseProduct(String type) {
        AppStoreProduct product = new AppStoreProduct();
        product.setCode("commercial-test");
        product.setName("Commercial Test");
        product.setProductType(type);
        product.setPriceCents(990);
        product.setScoreAmount(0);
        product.setGoldCoinAmount(0);
        product.setVipType(0);
        product.setVipDays(0);
        return product;
    }
}

package com.example.sillyspringboot.billing.mapper;

import com.example.sillyspringboot.billing.entity.AppPaymentOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AppPaymentOrderExpirationMapperIntegrationTest {

    @Autowired
    private AppPaymentOrderMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void hidesExpiredPendingOrdersAndAllowsLateSettlement() {
        long userId = insertUser();
        long productId = jdbc.queryForObject(
                "SELECT id FROM app_store_product WHERE code = 'coin_small'",
                Long.class
        );
        AppPaymentOrder expired = insertOrder(userId, productId, "PENDING", LocalDateTime.now().minusSeconds(1));
        AppPaymentOrder active = insertOrder(userId, productId, "PENDING", LocalDateTime.now().plusMinutes(1));
        AppPaymentOrder paid = insertOrder(userId, productId, "PAID", LocalDateTime.now().minusSeconds(1));

        assertThat(mapper.listByUserId(userId, 10))
                .extracting(AppPaymentOrder::getOrderNo)
                .containsExactlyInAnyOrder(active.getOrderNo(), paid.getOrderNo());
        assertThat(mapper.countAdminList(null, null)).isEqualTo(2);

        assertThat(mapper.closeExpiredPending()).isEqualTo(1);
        assertThat(mapper.findByOrderNo(expired.getOrderNo()).getStatus()).isEqualTo("CLOSED");

        assertThat(mapper.markPaid(expired.getId(), "late-trade", 990, "late-hash")).isEqualTo(1);
        AppPaymentOrder recovered = mapper.findByOrderNo(expired.getOrderNo());
        assertThat(recovered.getStatus()).isEqualTo("PAID");
        assertThat(mapper.listByUserId(userId, 10))
                .extracting(AppPaymentOrder::getOrderNo)
                .contains(expired.getOrderNo());
    }

    private long insertUser() {
        long telegramId = 9_300_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100_000_000L);
        jdbc.update(
                "INSERT INTO app_user (telegram_user_id, username) VALUES (?, ?)",
                telegramId,
                "payment-expiration-test"
        );
        return jdbc.queryForObject(
                "SELECT id FROM app_user WHERE telegram_user_id = ?",
                Long.class,
                telegramId
        );
    }

    private AppPaymentOrder insertOrder(long userId, long productId, String status, LocalDateTime expiresAt) {
        AppPaymentOrder order = new AppPaymentOrder();
        order.setOrderNo("SPTEST" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase());
        order.setUserId(userId);
        order.setProductId(productId);
        order.setProductCode("coin_small");
        order.setProductName("小额钻石包");
        order.setProductType("COIN");
        order.setAmountCents(990);
        order.setScoreAmount(120);
        order.setGoldCoinAmount(1200);
        order.setVipType(0);
        order.setVipDays(0);
        order.setPaymentChannel("epay");
        order.setStatus(status);
        order.setExpiresAt(expiresAt);
        mapper.insert(order);
        return mapper.findByOrderNo(order.getOrderNo());
    }
}

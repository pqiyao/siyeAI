package com.example.sillyspringboot.billing.service;

import com.example.sillyspringboot.billing.mapper.AppPaymentOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentOrderExpirationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrderExpirationService.class);

    private final AppPaymentOrderMapper orderMapper;

    public PaymentOrderExpirationService(AppPaymentOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Scheduled(initialDelay = 10_000L, fixedDelay = 10_000L)
    @Transactional
    public void closeExpiredOrders() {
        int closed = orderMapper.closeExpiredPending();
        if (closed > 0) {
            log.info("closed expired pending payment orders count={}", closed);
        }
    }
}

package com.example.sillyspringboot.billing.service;

import com.example.sillyspringboot.billing.mapper.AppPaymentOrderMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentOrderExpirationServiceTest {

    @Test
    void closesExpiredPendingOrders() {
        AppPaymentOrderMapper mapper = mock(AppPaymentOrderMapper.class);
        when(mapper.closeExpiredPending()).thenReturn(2);

        new PaymentOrderExpirationService(mapper).closeExpiredOrders();

        verify(mapper).closeExpiredPending();
    }
}

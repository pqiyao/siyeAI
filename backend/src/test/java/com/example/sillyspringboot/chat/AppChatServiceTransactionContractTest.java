package com.example.sillyspringboot.chat;

import com.example.sillyspringboot.chat.service.AppChatService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class AppChatServiceTransactionContractTest {

    @Test
    void regeneratePublicOverloadsRemainTransactional() throws Exception {
        assertTransactional("promoteRegenerateVariant", long.class, long.class, long.class, String.class);
        assertTransactional("promoteRegenerateVariant", long.class, long.class, long.class, String.class, boolean.class);
    }

    @Test
    void continuePublicOverloadsRemainTransactional() throws Exception {
        assertTransactional(
                "finalizeContinueAsMessage",
                long.class, long.class, long.class, long.class, String.class, String.class
        );
        assertTransactional(
                "finalizeContinueAsMessage",
                long.class, long.class, long.class, long.class, String.class, String.class, boolean.class
        );
    }

    private static void assertTransactional(String methodName, Class<?>... parameterTypes) throws Exception {
        Transactional annotation = AppChatService.class
                .getMethod(methodName, parameterTypes)
                .getAnnotation(Transactional.class);
        assertThat(annotation)
                .as("%s%s must be a Spring transaction entry point", methodName, java.util.Arrays.toString(parameterTypes))
                .isNotNull();
    }
}

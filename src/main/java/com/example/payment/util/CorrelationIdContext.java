package com.example.payment.util;

import org.slf4j.MDC;

import java.util.UUID;

public final class CorrelationIdContext {

    public static final String HEADER_NAME = "X-Correlation-ID";

    private CorrelationIdContext() {
    }

    public static String get() {
        return MDC.get("correlationId");
    }

    public static String getOrGenerate() {
        String correlationId = get();
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            MDC.put("correlationId", correlationId);
        }
        return correlationId;
    }
}

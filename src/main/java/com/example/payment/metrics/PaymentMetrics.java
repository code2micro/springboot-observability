package com.example.payment.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {

    private final MeterRegistry meterRegistry;

    public PaymentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incrementPaymentProcessing() {
        Counter.builder("payment.processing.count")
                .description("Count of payment processing requests")
                .register(meterRegistry)
                .increment();
    }

    public void incrementPaymentSuccess() {
        Counter.builder("payment.success.count")
                .description("Count of successful payments")
                .register(meterRegistry)
                .increment();
    }

    public void incrementPaymentFailure() {
        Counter.builder("payment.failure.count")
                .description("Count of failed payments")
                .register(meterRegistry)
                .increment();
    }

    public void incrementScorifyFailure() {
        Counter.builder("scorify.failure.count")
                .description("Count of failed calls to Scorify")
                .register(meterRegistry)
                .increment();
    }

    public Timer.Sample startPaymentTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopPaymentTimer(Timer.Sample sample) {
        sample.stop(meterRegistry.timer("payment.processing.duration"));
    }

    public Timer.Sample startScorifyTimer() {
        return Timer.start(meterRegistry);
    }

    public void stopScorifyTimer(Timer.Sample sample) {
        sample.stop(meterRegistry.timer("scorify.call.duration"));
    }
}

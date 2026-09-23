package com.example.payment.client;

import com.example.payment.dto.ScorifyResponse;
import com.example.payment.exception.DownstreamServiceException;
import com.example.payment.metrics.PaymentMetrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class ScorifyClient {

    private static final Logger log = LoggerFactory.getLogger(ScorifyClient.class);

    private final RestTemplate restTemplate;
    private final PaymentMetrics paymentMetrics;

    @Value("${app.downstream.scorify.url:http://localhost:8080/mock/scorify}")
    private String scorifyUrl;

    @Value("${app.downstream.scorify.delay-ms:0}")
    private long delayMs;

    public ScorifyClient(RestTemplate restTemplate, PaymentMetrics paymentMetrics) {
        this.restTemplate = restTemplate;
        this.paymentMetrics = paymentMetrics;
    }

    public ScorifyResponse callScorify(String transactionId, Long amount, String correlationId) {
        String url = scorifyUrl + "?transactionId={transactionId}&amount={amount}&delay={delay}";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Correlation-ID", correlationId);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        Timer.Sample sample = paymentMetrics.startScorifyTimer();
        try {
            ResponseEntity<ScorifyResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    ScorifyResponse.class,
                    transactionId,
                    amount,
                    delayMs);

            ScorifyResponse body = response.getBody();
            if (body == null || !"APPROVED".equalsIgnoreCase(body.getStatus())) {
                paymentMetrics.incrementScorifyFailure();
                throw new DownstreamServiceException("Scorify rejected transactionId=" + transactionId);
            }

            log.info("Scorify response received transactionId={} status={} correlationId={}", transactionId, body.getStatus(), correlationId);
            return body;
        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            paymentMetrics.incrementScorifyFailure();
            log.error("Scorify HTTP failure for transactionId={} statusCode={} correlationId={}", transactionId, ex.getStatusCode(), correlationId, ex);
            throw new DownstreamServiceException("Scorify returned HTTP " + ex.getStatusCode().value() + " for transactionId=" + transactionId, ex);
        } catch (ResourceAccessException ex) {
            paymentMetrics.incrementScorifyFailure();
            log.error("Scorify timeout or network failure transactionId={} correlationId={}", transactionId, correlationId, ex);
            throw new DownstreamServiceException("Scorify timeout or network issue for transactionId=" + transactionId, ex);
        } finally {
            paymentMetrics.stopScorifyTimer(sample);
        }
    }
}

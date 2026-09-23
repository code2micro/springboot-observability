package com.example.payment.client;

import com.example.payment.dto.AccountValidationResponse;
import com.example.payment.exception.DownstreamServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AccountClient {

    private static final Logger log = LoggerFactory.getLogger(AccountClient.class);

    private final RestTemplate restTemplate;

    @Value("${app.downstream.account-service.url:http://localhost:8080/mock/account}")
    private String accountServiceUrl;

    public AccountClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public AccountValidationResponse validateAccount(String accountId, String correlationId) {
        String url = accountServiceUrl + "/validate?accountId={accountId}";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Correlation-ID", correlationId);

        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        try {
            ResponseEntity<AccountValidationResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    requestEntity,
                    AccountValidationResponse.class,
                    accountId);

            AccountValidationResponse body = response.getBody();
            if (body == null || !body.isValid()) {
                throw new DownstreamServiceException("Account validation failed for accountId=" + accountId);
            }
            log.info("Account Service response received accountId={} valid={} correlationId={}", accountId, body.isValid(), correlationId);
            return body;
        } catch (RestClientException ex) {
            log.error("Account Service call failed for accountId={} correlationId={}", accountId, correlationId, ex);
            throw new DownstreamServiceException("Account Service unavailable for accountId=" + accountId, ex);
        }
    }
}

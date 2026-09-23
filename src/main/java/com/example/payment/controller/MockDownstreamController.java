package com.example.payment.controller;

import com.example.payment.dto.AccountValidationResponse;
import com.example.payment.dto.ScorifyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock")
public class MockDownstreamController {

    private static final Logger log = LoggerFactory.getLogger(MockDownstreamController.class);

    @GetMapping("/account/validate")
    public ResponseEntity<AccountValidationResponse> validateAccount(@RequestParam String accountId,
                                                                   @RequestParam(required = false, defaultValue = "false") boolean fail) {
        log.info("Mock Account Service received accountId={} fail={}", accountId, fail);

        if (fail) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new AccountValidationResponse(accountId, false, 0L));
        }

        return ResponseEntity.ok(new AccountValidationResponse(accountId, true, 50000L));
    }

    @GetMapping("/scorify")
    public ResponseEntity<ScorifyResponse> scorify(@RequestParam(required = false) String transactionId,
                                                 @RequestParam(required = false) Long amount,
                                                 @RequestParam(required = false) Long delay,
                                                 @RequestParam(required = false) Integer status) throws InterruptedException {
        if (delay != null && delay > 0) {
            log.info("Mock Scorify delaying response by {} ms for transactionId={}", delay, transactionId);
            Thread.sleep(delay);
        }

        if (status != null) {
            log.warn("Mock Scorify simulating HTTP {} for transactionId={}", status, transactionId);
            return ResponseEntity.status(status)
                    .body(new ScorifyResponse("REJECTED", transactionId, "Simulated failure"));
        }

        log.info("Mock Scorify completed successfully for transactionId={} amount={}", transactionId, amount);
        return ResponseEntity.ok(new ScorifyResponse("APPROVED", transactionId, "OK"));
    }
}

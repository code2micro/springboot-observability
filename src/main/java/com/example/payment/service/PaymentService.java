package com.example.payment.service;

import com.example.payment.client.AccountClient;
import com.example.payment.client.ScorifyClient;
import com.example.payment.dto.AccountValidationResponse;
import com.example.payment.dto.PaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.dto.ScorifyResponse;
import com.example.payment.entity.PaymentEntity;
import com.example.payment.entity.PaymentStatus;
import com.example.payment.exception.DownstreamServiceException;
import com.example.payment.exception.PaymentNotFoundException;
import com.example.payment.exception.ValidationException;
import com.example.payment.metrics.PaymentMetrics;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.util.CorrelationIdContext;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final AccountClient accountClient;
    private final ScorifyClient scorifyClient;
    private final PaymentMetrics paymentMetrics;

    public PaymentService(PaymentRepository paymentRepository,
                          AccountClient accountClient,
                          ScorifyClient scorifyClient,
                          PaymentMetrics paymentMetrics) {
        this.paymentRepository = paymentRepository;
        this.accountClient = accountClient;
        this.scorifyClient = scorifyClient;
        this.paymentMetrics = paymentMetrics;
    }

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request) {
        validateRequest(request);
        String correlationId = CorrelationIdContext.getOrGenerate();

        Timer.Sample paymentSample = paymentMetrics.startPaymentTimer();
        paymentMetrics.incrementPaymentProcessing();

        log.info("Payment received transactionId={} accountId={} amount={} correlationId={}",
                request.getTransactionId(), request.getAccountId(), request.getAmount(), correlationId);

        PaymentEntity payment = new PaymentEntity();
        payment.setTransactionId(request.getTransactionId());
        payment.setAccountId(request.getAccountId());
        payment.setAmount(request.getAmount());
        payment.setStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(payment);

        try {
            log.info("Payment validation successful transactionId={} correlationId={}", request.getTransactionId(), correlationId);

            log.info("Calling Account Service transactionId={} accountId={} correlationId={}",
                    request.getTransactionId(), request.getAccountId(), correlationId);
            AccountValidationResponse accountResponse = accountClient.validateAccount(request.getAccountId(), correlationId);
            if (accountResponse == null || !accountResponse.isValid()) {
                throw new DownstreamServiceException("Account validation failed for accountId=" + request.getAccountId());
            }

            log.info("Account Service response received transactionId={} accountId={} correlationId={}",
                    request.getTransactionId(), request.getAccountId(), correlationId);

            log.info("Calling Scorify transactionId={} amount={} correlationId={}",
                    request.getTransactionId(), request.getAmount(), correlationId);
            ScorifyResponse scorifyResponse = scorifyClient.callScorify(request.getTransactionId(), request.getAmount(), correlationId);
            log.info("Scorify response received transactionId={} status={} correlationId={}",
                    request.getTransactionId(), scorifyResponse.getStatus(), correlationId);

            payment.setStatus(PaymentStatus.APPROVED);
            paymentRepository.save(payment);
            paymentMetrics.incrementPaymentSuccess();
            paymentMetrics.stopPaymentTimer(paymentSample);
            log.info("Payment completed transactionId={} correlationId={}", request.getTransactionId(), correlationId);
            return PaymentResponse.fromEntity(payment, correlationId);
        } catch (DownstreamServiceException ex) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            paymentMetrics.incrementPaymentFailure();
            paymentMetrics.stopPaymentTimer(paymentSample);
            log.error("Payment failed transactionId={} correlationId={} reason={}",
                    request.getTransactionId(), correlationId, ex.getMessage(), ex);
            throw ex;
        } catch (Exception ex) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            paymentMetrics.incrementPaymentFailure();
            paymentMetrics.stopPaymentTimer(paymentSample);
            log.error("Payment failed transactionId={} correlationId={} reason={}",
                    request.getTransactionId(), correlationId, ex.getMessage(), ex);
            throw new DownstreamServiceException("Unexpected payment processing failure", ex);
        }
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPayment(Long id) {
        PaymentEntity payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for id=" + id));
        return PaymentResponse.fromEntity(payment, CorrelationIdContext.getOrGenerate());
    }

    private void validateRequest(PaymentRequest request) {
        if (request == null) {
            throw new ValidationException("Payment request cannot be null");
        }
        if (request.getTransactionId() == null || request.getTransactionId().isBlank()) {
            throw new ValidationException("transactionId is required");
        }
        if (request.getAccountId() == null || request.getAccountId().isBlank()) {
            throw new ValidationException("accountId is required");
        }
        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new ValidationException("amount must be greater than zero");
        }
    }
}

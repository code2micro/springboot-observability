package com.example.payment;

import com.example.payment.controller.PaymentController;
import com.example.payment.dto.PaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.entity.PaymentStatus;
import com.example.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class PaymentControllerIntegrationTest {

    @Autowired
    private PaymentController paymentController;

    @MockBean
    private PaymentService paymentService;

    @Test
    void shouldCreatePayment() {
        PaymentRequest request = new PaymentRequest();
        request.setTransactionId("TXN1001");
        request.setAccountId("ACC100");
        request.setAmount(5000L);

        PaymentResponse expected = new PaymentResponse();
        expected.setTransactionId("TXN1001");
        expected.setAccountId("ACC100");
        expected.setAmount(5000L);
        expected.setStatus(PaymentStatus.APPROVED);
        when(paymentService.createPayment(any(PaymentRequest.class))).thenReturn(expected);

        PaymentResponse response = paymentController.createPayment(request);

        assertThat(response).isNotNull();
        assertThat(response.getTransactionId()).isEqualTo("TXN1001");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }
}

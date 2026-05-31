package com.afrisanjaya.shipment.billing;

import com.afrisanjaya.shipment.billing.api.dto.PaymentRequest;
import com.afrisanjaya.shipment.billing.api.dto.TopUpRequest;
import com.afrisanjaya.shipment.billing.api.dto.TransactionResponse;
import com.afrisanjaya.shipment.billing.api.dto.WalletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class WalletIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("wallet_db")
            .withUsername("wallet_test")
            .withPassword("wallet_test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl() {
        return "http://localhost:" + port + "/api/v1/wallet";
    }

    @Test
    void fullFlowTopUpThenPay() {
        UUID userId = UUID.randomUUID();

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId.toString());
        ResponseEntity<WalletResponse> walletResp = restTemplate.exchange(
                baseUrl(), HttpMethod.GET, new HttpEntity<>(headers), WalletResponse.class);

        assertThat(walletResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(walletResp.getBody().balance()).isEqualByComparingTo(BigDecimal.ZERO);

        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        TopUpRequest topUp = new TopUpRequest(new BigDecimal("100000"), "Integration test top-up");
        ResponseEntity<TransactionResponse> topUpResp = restTemplate.exchange(
                baseUrl() + "/topup", HttpMethod.POST,
                new HttpEntity<>(topUp, headers), TransactionResponse.class);

        assertThat(topUpResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(topUpResp.getBody().amount()).isEqualByComparingTo(new BigDecimal("100000"));

        headers.remove("Idempotency-Key");
        walletResp = restTemplate.exchange(
                baseUrl(), HttpMethod.GET, new HttpEntity<>(headers), WalletResponse.class);
        assertThat(walletResp.getBody().balance()).isEqualByComparingTo(new BigDecimal("100000"));

        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        PaymentRequest pay = new PaymentRequest(UUID.randomUUID(), new BigDecimal("30000"));
        ResponseEntity<TransactionResponse> payResp = restTemplate.exchange(
                baseUrl() + "/pay", HttpMethod.POST,
                new HttpEntity<>(pay, headers), TransactionResponse.class);

        assertThat(payResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        headers.remove("Idempotency-Key");
        walletResp = restTemplate.exchange(
                baseUrl(), HttpMethod.GET, new HttpEntity<>(headers), WalletResponse.class);
        assertThat(walletResp.getBody().balance()).isEqualByComparingTo(new BigDecimal("70000"));
    }

    @Test
    void concurrentPaymentsShouldPreventDoubleSpend() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId.toString());

        restTemplate.exchange(baseUrl(), HttpMethod.GET, new HttpEntity<>(headers), WalletResponse.class);
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        restTemplate.exchange(baseUrl() + "/topup", HttpMethod.POST,
                new HttpEntity<>(new TopUpRequest(new BigDecimal("100000"), null), headers), TransactionResponse.class);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);

        Runnable payTask = () -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                HttpHeaders h = new HttpHeaders();
                h.set("X-User-Id", userId.toString());
                h.set("Idempotency-Key", UUID.randomUUID().toString());
                ResponseEntity<TransactionResponse> resp = restTemplate.exchange(
                        baseUrl() + "/pay", HttpMethod.POST,
                        new HttpEntity<>(new PaymentRequest(UUID.randomUUID(), new BigDecimal("60000")), h),
                        TransactionResponse.class);
                if (resp.getStatusCode() == HttpStatus.OK) successCount.incrementAndGet();
                if (resp.getStatusCode() == HttpStatus.CONFLICT) conflictCount.incrementAndGet();
            } finally {
                done.countDown();
            }
        };

        var executor = Executors.newFixedThreadPool(2);
        executor.submit(payTask);
        executor.submit(payTask);
        latch.countDown();
        done.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(successCount.get() + conflictCount.get()).isEqualTo(2);

        headers.remove("Idempotency-Key");
        ResponseEntity<WalletResponse> walletResp = restTemplate.exchange(
                baseUrl(), HttpMethod.GET, new HttpEntity<>(headers), WalletResponse.class);
        assertThat(walletResp.getBody().balance()).isEqualByComparingTo(new BigDecimal("40000"));
    }

    @Test
    void idempotencySameKeyReturnsCachedResponse() {
        UUID userId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId.toString());
        headers.set("Idempotency-Key", idempotencyKey.toString());

        TopUpRequest topUp = new TopUpRequest(new BigDecimal("50000"), "Idempotency test");
        ResponseEntity<TransactionResponse> first = restTemplate.exchange(
                baseUrl() + "/topup", HttpMethod.POST,
                new HttpEntity<>(topUp, headers), TransactionResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<TransactionResponse> second = restTemplate.exchange(
                baseUrl() + "/topup", HttpMethod.POST,
                new HttpEntity<>(topUp, headers), TransactionResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(second.getBody().transactionId()).isEqualTo(first.getBody().transactionId());
        assertThat(second.getBody().balanceAfter()).isEqualByComparingTo(first.getBody().balanceAfter());
        assertThat(second.getBody().balanceBefore()).isEqualByComparingTo(first.getBody().balanceBefore());

        headers.remove("Idempotency-Key");
        ResponseEntity<WalletResponse> walletResp = restTemplate.exchange(
                baseUrl(), HttpMethod.GET, new HttpEntity<>(headers), WalletResponse.class);
        assertThat(walletResp.getBody().balance()).isEqualByComparingTo(new BigDecimal("50000"));
    }
}

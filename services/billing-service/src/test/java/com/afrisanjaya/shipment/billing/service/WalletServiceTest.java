package com.afrisanjaya.shipment.billing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.afrisanjaya.shipment.billing.api.dto.PaymentRequest;
import com.afrisanjaya.shipment.billing.api.dto.TopUpRequest;
import com.afrisanjaya.shipment.billing.api.dto.TransactionResponse;
import com.afrisanjaya.shipment.billing.api.dto.WalletResponse;
import com.afrisanjaya.shipment.billing.api.exception.InsufficientBalanceException;
import com.afrisanjaya.shipment.billing.api.exception.WalletNotFoundException;
import com.afrisanjaya.shipment.billing.domain.entity.Wallet;
import com.afrisanjaya.shipment.billing.domain.enums.TransactionType;
import com.afrisanjaya.shipment.billing.domain.repository.IdempotencyKeyRepository;
import com.afrisanjaya.shipment.billing.domain.repository.WalletRepository;
import com.afrisanjaya.shipment.billing.messaging.WalletTransactionQueuePublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    private static final UUID SYSTEM_WALLET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock private WalletRepository walletRepository;
    @Mock private IdempotencyKeyRepository idempotencyKeyRepository;
    @Mock private LedgerService ledgerService;
    @Mock private OutboxService outboxService;
    @Mock private WalletTransactionQueuePublisher walletTransactionQueuePublisher;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private WalletService walletService;

    @Test
    void getWalletBalanceShouldAutoCreateWalletForNewUser() {
        UUID userId = UUID.randomUUID();
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> {
            Wallet w = inv.getArgument(0);
            w.setId(UUID.randomUUID());
            return w;
        });

        WalletResponse response = walletService.getWalletBalance(userId);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.currency()).isEqualTo("IDR");
        verify(walletRepository).save(any(Wallet.class));
    }

    @Test
    void getWalletBalanceShouldReturnExistingWallet() {
        UUID userId = UUID.randomUUID();
        UUID walletId = UUID.randomUUID();
        Wallet wallet = Wallet.builder()
                .id(walletId).userId(userId)
                .balance(new BigDecimal("150000")).currency("IDR")
                .isActive(true).version(0L).build();

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        WalletResponse response = walletService.getWalletBalance(userId);

        assertThat(response.walletId()).isEqualTo(walletId);
        assertThat(response.balance()).isEqualByComparingTo(new BigDecimal("150000"));
        verify(walletRepository, never()).save(any());
    }

    @Test
    void topUpShouldRecordLedgerAndUpdateBalances() {
        UUID userId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        TopUpRequest request = new TopUpRequest(new BigDecimal("100000"), "Test top-up");

        Wallet userWallet = Wallet.builder()
                .id(UUID.randomUUID()).userId(userId)
                .balance(BigDecimal.ZERO).currency("IDR").isActive(true).version(0L).build();
        Wallet systemWallet = Wallet.builder()
                .id(SYSTEM_WALLET_ID).userId(UUID.randomUUID())
                .balance(new BigDecimal("1000000000")).currency("IDR").isActive(true).version(0L).build();

        when(idempotencyKeyRepository.findById(idempotencyKey)).thenReturn(Optional.empty());
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(userWallet));
        when(walletRepository.findById(SYSTEM_WALLET_ID)).thenReturn(Optional.of(systemWallet));
        when(idempotencyKeyRepository.saveAndFlush(any())).thenReturn(null);
        doNothing().when(ledgerService).recordDoubleEntry(any(), any(), any(), any(), any(), any(), any(), any(), any());
        doNothing().when(outboxService).publishEvent(any(), any(), any());

        TransactionResponse response = walletService.topUp(userId, idempotencyKey, request);

        assertThat(response.type()).isEqualTo(TransactionType.TOPUP);
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("100000"));

        verify(ledgerService).recordDoubleEntry(any(), eq(TransactionType.TOPUP), eq(new BigDecimal("100000")),
                eq(systemWallet), any(), eq(userWallet), any(), isNull(), eq("Test top-up"));
        verify(walletRepository).save(systemWallet);
        verify(walletRepository).save(userWallet);
        verify(walletTransactionQueuePublisher).publish(any());
    }

    @Test
    void processPaymentShouldRecordLedgerAndUpdateBalances() {
        UUID userId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        PaymentRequest request = new PaymentRequest(bookingId, new BigDecimal("50000"));

        Wallet userWallet = Wallet.builder()
                .id(UUID.randomUUID()).userId(userId)
                .balance(new BigDecimal("100000")).currency("IDR").isActive(true).version(0L).build();
        Wallet systemWallet = Wallet.builder()
                .id(SYSTEM_WALLET_ID).userId(UUID.randomUUID())
                .balance(new BigDecimal("1000000000")).currency("IDR").isActive(true).version(0L).build();

        when(idempotencyKeyRepository.findById(idempotencyKey)).thenReturn(Optional.empty());
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(userWallet));
        when(walletRepository.findById(SYSTEM_WALLET_ID)).thenReturn(Optional.of(systemWallet));
        when(idempotencyKeyRepository.saveAndFlush(any())).thenReturn(null);
        doNothing().when(ledgerService).recordDoubleEntry(any(), any(), any(), any(), any(), any(), any(), any(), any());
        doNothing().when(outboxService).publishEvent(any(), any(), any());

        TransactionResponse response = walletService.processPayment(userId, idempotencyKey, request);

        assertThat(response.type()).isEqualTo(TransactionType.PAYMENT);
        assertThat(response.amount()).isEqualByComparingTo(new BigDecimal("50000"));

        verify(ledgerService).recordDoubleEntry(any(), eq(TransactionType.PAYMENT), eq(new BigDecimal("50000")),
                eq(userWallet), any(), eq(systemWallet), any(), eq(bookingId), any());
        verify(walletTransactionQueuePublisher).publish(any());
    }

    @Test
    void processPaymentShouldThrowWhenInsufficientBalance() {
        UUID userId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        PaymentRequest request = new PaymentRequest(UUID.randomUUID(), new BigDecimal("500000"));

        Wallet userWallet = Wallet.builder()
                .id(UUID.randomUUID()).userId(userId)
                .balance(new BigDecimal("1000")).currency("IDR").isActive(true).version(0L).build();
        Wallet systemWallet = Wallet.builder()
                .id(SYSTEM_WALLET_ID).userId(UUID.randomUUID())
                .balance(new BigDecimal("1000000000")).currency("IDR").isActive(true).version(0L).build();

        when(idempotencyKeyRepository.findById(idempotencyKey)).thenReturn(Optional.empty());
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(userWallet));
        when(walletRepository.findById(SYSTEM_WALLET_ID)).thenReturn(Optional.of(systemWallet));
        doNothing().when(outboxService).publishEvent(any(), any(), any());

        assertThatThrownBy(() -> walletService.processPayment(userId, idempotencyKey, request))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(ledgerService, never()).recordDoubleEntry(any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(walletTransactionQueuePublisher, never()).publish(any());
    }

    @Test
    void processPaymentShouldThrowWhenWalletNotFound() {
        UUID userId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        PaymentRequest request = new PaymentRequest(UUID.randomUUID(), new BigDecimal("50000"));

        when(idempotencyKeyRepository.findById(idempotencyKey)).thenReturn(Optional.empty());
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.processPayment(userId, idempotencyKey, request))
                .isInstanceOf(WalletNotFoundException.class);
    }
}

package com.afrisanjaya.shipment.billing.service;

import com.afrisanjaya.shipment.billing.domain.entity.LedgerEntry;
import com.afrisanjaya.shipment.billing.domain.entity.Wallet;
import com.afrisanjaya.shipment.billing.domain.enums.EntryType;
import com.afrisanjaya.shipment.billing.domain.enums.TransactionType;
import com.afrisanjaya.shipment.billing.domain.repository.LedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private LedgerService ledgerService;

    @Test
    void recordDoubleEntryShouldCreateBalancedDebitAndCredit() {
        UUID txId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("50000");

        Wallet debitWallet = Wallet.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID())
                .balance(new BigDecimal("100000")).currency("IDR").build();
        Wallet creditWallet = Wallet.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID())
                .balance(new BigDecimal("50000")).currency("IDR").build();

        BigDecimal debitBefore = debitWallet.getBalance();
        BigDecimal creditBefore = creditWallet.getBalance();

        ledgerService.recordDoubleEntry(txId, TransactionType.PAYMENT, amount,
                debitWallet, debitBefore, creditWallet, creditBefore,
                UUID.randomUUID(), "Test payment");

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(captor.capture());

        LedgerEntry debit = captor.getAllValues().get(0);
        LedgerEntry credit = captor.getAllValues().get(1);

        assertThat(debit.getEntryType()).isEqualTo(EntryType.DEBIT);
        assertThat(debit.getAmount()).isEqualByComparingTo(amount);
        assertThat(debit.getBalanceBefore()).isEqualByComparingTo(debitBefore);
        assertThat(debit.getBalanceAfter()).isEqualByComparingTo(debitBefore.subtract(amount));
        assertThat(debit.getTransactionId()).isEqualTo(txId);

        assertThat(credit.getEntryType()).isEqualTo(EntryType.CREDIT);
        assertThat(credit.getAmount()).isEqualByComparingTo(amount);
        assertThat(credit.getBalanceBefore()).isEqualByComparingTo(creditBefore);
        assertThat(credit.getBalanceAfter()).isEqualByComparingTo(creditBefore.add(amount));
        assertThat(credit.getTransactionId()).isEqualTo(txId);

        assertThat(debit.getAmount()).isEqualByComparingTo(credit.getAmount());
    }

    @Test
    void recordDoubleEntryShouldRejectZeroAmount() {
        Wallet w1 = Wallet.builder().id(UUID.randomUUID()).userId(UUID.randomUUID())
                .balance(BigDecimal.ZERO).currency("IDR").build();
        Wallet w2 = Wallet.builder().id(UUID.randomUUID()).userId(UUID.randomUUID())
                .balance(BigDecimal.ZERO).currency("IDR").build();

        assertThatThrownBy(() ->
                ledgerService.recordDoubleEntry(UUID.randomUUID(), TransactionType.TOPUP,
                        BigDecimal.ZERO, w1, BigDecimal.ZERO, w2, BigDecimal.ZERO,
                        null, "Zero test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }

    @Test
    void recordDoubleEntryShouldRejectNegativeAmount() {
        Wallet w1 = Wallet.builder().id(UUID.randomUUID()).userId(UUID.randomUUID())
                .balance(BigDecimal.ZERO).currency("IDR").build();
        Wallet w2 = Wallet.builder().id(UUID.randomUUID()).userId(UUID.randomUUID())
                .balance(BigDecimal.ZERO).currency("IDR").build();

        assertThatThrownBy(() ->
                ledgerService.recordDoubleEntry(UUID.randomUUID(), TransactionType.TOPUP,
                        new BigDecimal("-100"), w1, BigDecimal.ZERO, w2, BigDecimal.ZERO,
                        null, "Negative test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("greater than zero");
    }
}

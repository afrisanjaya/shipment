package com.afrisanjaya.shipment.billing.service;

import com.afrisanjaya.shipment.billing.domain.entity.LedgerEntry;
import com.afrisanjaya.shipment.billing.domain.entity.Wallet;
import com.afrisanjaya.shipment.billing.domain.enums.EntryType;
import com.afrisanjaya.shipment.billing.domain.enums.TransactionType;
import com.afrisanjaya.shipment.billing.domain.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public void recordDoubleEntry(
            UUID transactionId,
            TransactionType type,
            BigDecimal amount,
            Wallet debitWallet,
            BigDecimal debitBalanceBefore,
            Wallet creditWallet,
            BigDecimal creditBalanceBefore,
            UUID referenceId,
            String description) {

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Ledger entry amount must be greater than zero");
        }

        if (ledgerEntryRepository.existsByTransactionId(transactionId)) {
            log.debug("Skipping duplicate ledger transaction: txId={}, type={}", transactionId, type);
            return;
        }

        LedgerEntry debit = LedgerEntry.builder()
                .transactionId(transactionId)
                .wallet(debitWallet)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .currency(debitWallet.getCurrency())
                .transactionType(type)
                .referenceId(referenceId)
                .description(description)
                .balanceBefore(debitBalanceBefore)
                .balanceAfter(debitBalanceBefore.subtract(amount))
                .build();

        LedgerEntry credit = LedgerEntry.builder()
                .transactionId(transactionId)
                .wallet(creditWallet)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .currency(creditWallet.getCurrency())
                .transactionType(type)
                .referenceId(referenceId)
                .description(description)
                .balanceBefore(creditBalanceBefore)
                .balanceAfter(creditBalanceBefore.add(amount))
                .build();

        ledgerEntryRepository.save(debit);
        ledgerEntryRepository.save(credit);

        log.debug("Recorded double entry: txId={}, amount={}, type={}", transactionId, amount, type);
    }

    public boolean hasTransaction(UUID transactionId) {
        return ledgerEntryRepository.existsByTransactionId(transactionId);
    }

    @Transactional(readOnly = true)
    public LedgerSummary getSummaryByDate(LocalDate date) {
        List<Object[]> results = ledgerEntryRepository.sumByEntryTypeAndDate(date);

        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;

        for (Object[] row : results) {
            EntryType entryType = (EntryType) row[0];
            BigDecimal sum = (BigDecimal) row[1];
            if (entryType == EntryType.DEBIT) {
                totalDebits = sum;
            } else if (entryType == EntryType.CREDIT) {
                totalCredits = sum;
            }
        }

        return new LedgerSummary(totalDebits, totalCredits);
    }
}

package com.afrisanjaya.shipment.billing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.afrisanjaya.shipment.billing.api.dto.PaymentRequest;
import com.afrisanjaya.shipment.billing.api.dto.ReconciliationResponse;
import com.afrisanjaya.shipment.billing.api.dto.RefundRequest;
import com.afrisanjaya.shipment.billing.api.dto.StatementResponse;
import com.afrisanjaya.shipment.billing.api.dto.TopUpRequest;
import com.afrisanjaya.shipment.billing.api.dto.TransactionResponse;
import com.afrisanjaya.shipment.billing.api.dto.WalletResponse;
import com.afrisanjaya.shipment.billing.domain.entity.LedgerEntry;
import com.afrisanjaya.shipment.billing.domain.enums.EntryType;
import com.afrisanjaya.shipment.billing.api.exception.DuplicateIdempotencyKeyException;
import com.afrisanjaya.shipment.billing.api.exception.InsufficientBalanceException;
import com.afrisanjaya.shipment.billing.api.exception.WalletNotFoundException;
import com.afrisanjaya.shipment.billing.domain.entity.IdempotencyKey;
import com.afrisanjaya.shipment.billing.domain.entity.Wallet;
import com.afrisanjaya.shipment.billing.domain.enums.TransactionType;
import com.afrisanjaya.shipment.billing.domain.repository.IdempotencyKeyRepository;
import com.afrisanjaya.shipment.billing.domain.repository.LedgerEntryRepository;
import com.afrisanjaya.shipment.billing.domain.repository.WalletRepository;
import com.afrisanjaya.shipment.billing.messaging.WalletTransactionMessage;
import com.afrisanjaya.shipment.billing.messaging.WalletTransactionQueuePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private static final UUID SYSTEM_MERCHANT_WALLET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final WalletRepository walletRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerService ledgerService;
    private final OutboxService outboxService;
    private final WalletTransactionQueuePublisher walletTransactionQueuePublisher;
    private final ObjectMapper objectMapper;

    @Transactional
    public WalletResponse getWalletBalance(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultWallet(userId));
        return toWalletResponse(wallet);
    }

    @Transactional
    public TransactionResponse topUp(UUID userId, UUID idempotencyKey, TopUpRequest request) {
        return handleIdempotency(idempotencyKey, "TOPUP", () -> executeTopUp(userId, idempotencyKey, request));
    }

    @Transactional
    public TransactionResponse processPayment(UUID userId, UUID idempotencyKey, PaymentRequest request) {
        return handleIdempotency(idempotencyKey, "PAYMENT", () -> executePayment(userId, idempotencyKey, request));
    }

    @Transactional
    public TransactionResponse refund(UUID userId, UUID idempotencyKey, RefundRequest request) {
        return handleIdempotency(idempotencyKey, "REFUND", () -> executeRefund(userId, idempotencyKey, request));
    }

    private TransactionResponse executeTopUp(UUID userId, UUID idempotencyKey, TopUpRequest request) {
        Wallet userWallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultWallet(userId));
        Wallet systemWallet = getSystemMerchantWallet();

        if (systemWallet.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientBalanceException(
                    systemWallet.getId(), request.amount(), systemWallet.getBalance());
        }

        BigDecimal userBalanceBefore = userWallet.getBalance();
        BigDecimal systemBalanceBefore = systemWallet.getBalance();

        UUID txId = UUID.randomUUID();
        ledgerService.recordDoubleEntry(
                txId, TransactionType.TOPUP, request.amount(),
                systemWallet, systemBalanceBefore,
                userWallet, userBalanceBefore,
                null,
                request.description() != null ? request.description() : "Wallet Top-up"
        );

        systemWallet.setBalance(systemWallet.getBalance().subtract(request.amount()));
        userWallet.setBalance(userWallet.getBalance().add(request.amount()));

        walletRepository.save(systemWallet);
        walletRepository.save(userWallet);

        TransactionResponse response = new TransactionResponse(
                txId, TransactionType.TOPUP, request.amount(),
                userBalanceBefore, userWallet.getBalance(), userWallet.getUpdatedAt()
        );

        outboxService.publishEvent(response, userWallet.getId(), "WalletToppedUp_v1");
        publishTransactionAfterCommit(createTransactionMessage(
                txId,
                userWallet.getId(),
                idempotencyKey,
                TransactionType.TOPUP,
                request.amount(),
                systemWallet.getId(),
                userWallet.getId(),
                systemBalanceBefore,
                userBalanceBefore,
                null,
                request.description() != null ? request.description() : "Wallet Top-up",
                systemWallet.getCurrency()
        ));
        return response;
    }

    private TransactionResponse executePayment(UUID userId, UUID idempotencyKey, PaymentRequest request) {
        Wallet userWallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));
        Wallet systemWallet = getSystemMerchantWallet();

        if (userWallet.getBalance().compareTo(request.amount()) < 0) {
            outboxService.publishEvent(request, userWallet.getId(), "PaymentFailed_v1");
            throw new InsufficientBalanceException(userWallet.getId(), request.amount(), userWallet.getBalance());
        }

        BigDecimal userBalanceBefore = userWallet.getBalance();
        BigDecimal systemBalanceBefore = systemWallet.getBalance();

        UUID txId = UUID.randomUUID();
        ledgerService.recordDoubleEntry(
                txId, TransactionType.PAYMENT, request.amount(),
                userWallet, userBalanceBefore,
                systemWallet, systemBalanceBefore,
                request.bookingId(),
                "Booking Payment " + request.bookingId()
        );

        userWallet.setBalance(userWallet.getBalance().subtract(request.amount()));
        systemWallet.setBalance(systemWallet.getBalance().add(request.amount()));

        walletRepository.save(userWallet);
        walletRepository.save(systemWallet);

        TransactionResponse response = new TransactionResponse(
                txId, TransactionType.PAYMENT, request.amount(),
                userBalanceBefore, userWallet.getBalance(), userWallet.getUpdatedAt()
        );

        outboxService.publishEvent(response, userWallet.getId(), "PaymentCompleted_v1");
        publishTransactionAfterCommit(createTransactionMessage(
                txId,
                userWallet.getId(),
                idempotencyKey,
                TransactionType.PAYMENT,
                request.amount(),
                userWallet.getId(),
                systemWallet.getId(),
                userBalanceBefore,
                systemBalanceBefore,
                request.bookingId(),
                "Booking Payment " + request.bookingId(),
                userWallet.getCurrency()
        ));
        return response;
    }

    private TransactionResponse executeRefund(UUID userId, UUID idempotencyKey, RefundRequest request) {
        Wallet userWallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException(userId));
        Wallet systemWallet = getSystemMerchantWallet();

        if (systemWallet.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientBalanceException(
                    systemWallet.getId(), request.amount(), systemWallet.getBalance());
        }

        BigDecimal userBalanceBefore = userWallet.getBalance();
        BigDecimal systemBalanceBefore = systemWallet.getBalance();

        UUID txId = UUID.randomUUID();
        ledgerService.recordDoubleEntry(
                txId, TransactionType.REFUND, request.amount(),
                systemWallet, systemBalanceBefore,
                userWallet, userBalanceBefore,
                request.paymentTransactionId(),
                request.reason() != null ? request.reason() : "Refund for payment " + request.paymentTransactionId()
        );

        systemWallet.setBalance(systemWallet.getBalance().subtract(request.amount()));
        userWallet.setBalance(userWallet.getBalance().add(request.amount()));

        walletRepository.save(systemWallet);
        walletRepository.save(userWallet);

        TransactionResponse response = new TransactionResponse(
                txId, TransactionType.REFUND, request.amount(),
                userBalanceBefore, userWallet.getBalance(), userWallet.getUpdatedAt()
        );

        outboxService.publishEvent(response, userWallet.getId(), "RefundProcessed_v1");
        publishTransactionAfterCommit(createTransactionMessage(
                txId,
                userWallet.getId(),
                idempotencyKey,
                TransactionType.REFUND,
                request.amount(),
                systemWallet.getId(),
                userWallet.getId(),
                systemBalanceBefore,
                userBalanceBefore,
                request.paymentTransactionId(),
                request.reason() != null ? request.reason() : "Refund for payment " + request.paymentTransactionId(),
                systemWallet.getCurrency()
        ));
        return response;
    }

    private Wallet getSystemMerchantWallet() {
        return walletRepository.findById(SYSTEM_MERCHANT_WALLET_ID)
                .orElseThrow(() -> new IllegalStateException("System merchant wallet not found"));
    }

    @Transactional
    private Wallet createDefaultWallet(UUID userId) {
        Wallet wallet = Wallet.builder()
                .userId(userId)
                .balance(BigDecimal.ZERO)
                .currency("IDR")
                .isActive(true)
                .build();
        return walletRepository.save(wallet);
    }

    private TransactionResponse handleIdempotency(UUID key, String endpoint, Supplier<TransactionResponse> action) {
        Optional<IdempotencyKey> existing = idempotencyKeyRepository.findById(key);
        if (existing.isPresent()) {
            IdempotencyKey record = existing.get();
            if (!record.getEndpoint().equals(endpoint)) {
                throw new DuplicateIdempotencyKeyException(key);
            }
            log.info("Returning cached idempotency response for key: {}", key);
            return objectMapper.convertValue(record.getResponseBody(), TransactionResponse.class);
        }

        TransactionResponse result = action.get();

        IdempotencyKey record = IdempotencyKey.builder()
                .idempotencyKey(key)
                .endpoint(endpoint)
                .responseStatus(200)
                .responseBody(objectMapper.convertValue(result, Map.class))
                .build();
        try {
            idempotencyKeyRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent request with idempotency key {} completed first, returning cached result", key);
            return getExistingIdempotentResponse(key, endpoint);
        }

        return result;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private TransactionResponse getExistingIdempotentResponse(UUID key, String endpoint) {
        IdempotencyKey existing = idempotencyKeyRepository.findById(key).orElseThrow();
        if (!existing.getEndpoint().equals(endpoint)) {
            throw new DuplicateIdempotencyKeyException(key);
        }
        return objectMapper.convertValue(existing.getResponseBody(), TransactionResponse.class);
    }

    @Transactional
    public ReconciliationResponse reconcile(LocalDate date) {
        LedgerSummary summary = ledgerService.getSummaryByDate(date);
        return new ReconciliationResponse(
                date,
                summary.totalDebits(),
                summary.totalCredits(),
                summary.isBalanced(),
                summary.difference()
        );
    }

    @Transactional
    public StatementResponse getStatement(UUID userId, YearMonth month) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultWallet(userId));

        OffsetDateTime start = month.atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime end = month.atEndOfMonth().atTime(23, 59, 59).atOffset(ZoneOffset.UTC);

        List<LedgerEntry> entries = ledgerEntryRepository
                .findByWalletIdAndCreatedAtBetweenOrderByCreatedAtAsc(wallet.getId(), start, end);

        BigDecimal openingBalance;
        BigDecimal closingBalance;
        BigDecimal totalDebits;
        BigDecimal totalCredits;

        if (entries.isEmpty()) {
            openingBalance = wallet.getBalance();
            closingBalance = wallet.getBalance();
            totalDebits = BigDecimal.ZERO;
            totalCredits = BigDecimal.ZERO;
        } else {
            openingBalance = entries.get(0).getBalanceBefore();
            closingBalance = entries.get(entries.size() - 1).getBalanceAfter();

            Map<EntryType, BigDecimal> totals = entries.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            LedgerEntry::getEntryType,
                            java.util.stream.Collectors.reducing(
                                    BigDecimal.ZERO, LedgerEntry::getAmount, BigDecimal::add)));

            totalDebits  = totals.getOrDefault(EntryType.DEBIT,  BigDecimal.ZERO);
            totalCredits = totals.getOrDefault(EntryType.CREDIT, BigDecimal.ZERO);
        }

        List<TransactionResponse> transactions = entries.stream()
                .map(e -> new TransactionResponse(
                        e.getTransactionId(), e.getTransactionType(), e.getAmount(),
                        e.getBalanceBefore(), e.getBalanceAfter(), e.getCreatedAt()))
                .toList();

        return new StatementResponse(userId, wallet.getId(), month.toString(),
                openingBalance, closingBalance, totalDebits, totalCredits, transactions);
    }

    private WalletResponse toWalletResponse(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(), wallet.getUserId(), wallet.getBalance(), wallet.getCurrency(), wallet.getUpdatedAt()
        );
    }

    private WalletTransactionMessage createTransactionMessage(
            UUID transactionId,
            UUID groupId,
            UUID deduplicationId,
            TransactionType transactionType,
            BigDecimal amount,
            UUID debitWalletId,
            UUID creditWalletId,
            BigDecimal debitBalanceBefore,
            BigDecimal creditBalanceBefore,
            UUID referenceId,
            String description,
            String currency) {

        return new WalletTransactionMessage(
                transactionId,
                groupId,
                deduplicationId,
                transactionType,
                amount,
                debitWalletId,
                creditWalletId,
                debitBalanceBefore,
                creditBalanceBefore,
                referenceId,
                description,
                currency,
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private void publishTransactionAfterCommit(WalletTransactionMessage message) {
        Runnable publishAction = () -> {
            try {
                walletTransactionQueuePublisher.publish(message);
            } catch (Exception e) {
                log.error("Failed to publish wallet transaction message: transactionId={}, error={}",
                        message.transactionId(), e.getMessage(), e);
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishAction.run();
                }
            });
            return;
        }

        publishAction.run();
    }
}

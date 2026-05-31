package com.afrisanjaya.shipment.billing.api;

import com.afrisanjaya.shipment.billing.api.dto.PaymentRequest;
import com.afrisanjaya.shipment.billing.api.dto.ReconciliationResponse;
import com.afrisanjaya.shipment.billing.api.dto.RefundRequest;
import com.afrisanjaya.shipment.billing.api.dto.StatementResponse;
import com.afrisanjaya.shipment.billing.api.dto.TopUpRequest;
import com.afrisanjaya.shipment.billing.api.dto.TransactionResponse;
import com.afrisanjaya.shipment.billing.api.dto.WalletResponse;
import com.afrisanjaya.shipment.billing.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
@Tag(name = "Wallet API", description = "Fintech wallet with double-entry ledger")
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "Get wallet balance for authenticated user")
    @GetMapping
    public ResponseEntity<WalletResponse> getWalletBalance(
            @RequestHeader("X-User-Id") UUID userId) {
        
        return ResponseEntity.ok(walletService.getWalletBalance(userId));
    }

    @Operation(summary = "Top up wallet balance")
    @PostMapping("/topup")
    public ResponseEntity<TransactionResponse> topUpWallet(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody TopUpRequest request) {
        
        return ResponseEntity.ok(walletService.topUp(userId, idempotencyKey, request));
    }

    @Operation(summary = "Deduct balance for a booking payment")
    @PostMapping("/pay")
    public ResponseEntity<TransactionResponse> processPayment(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody PaymentRequest request) {
        
        return ResponseEntity.ok(walletService.processPayment(userId, idempotencyKey, request));
    }

    @Operation(summary = "Refund a payment — reverses the transaction")
    @PostMapping("/refund")
    public ResponseEntity<TransactionResponse> refund(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody RefundRequest request) {

        return ResponseEntity.ok(walletService.refund(userId, idempotencyKey, request));
    }

    @Operation(summary = "Reconcile ledger — verify all DEBITs equal all CREDITs for a date")
    @GetMapping("/reconcile")
    public ResponseEntity<ReconciliationResponse> reconcile(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return ResponseEntity.ok(walletService.reconcile(date));
    }

    @Operation(summary = "Get monthly statement with opening/closing balance and transactions")
    @GetMapping("/statement")
    public ResponseEntity<StatementResponse> getStatement(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestParam YearMonth month) {

        return ResponseEntity.ok(walletService.getStatement(userId, month));
    }
}

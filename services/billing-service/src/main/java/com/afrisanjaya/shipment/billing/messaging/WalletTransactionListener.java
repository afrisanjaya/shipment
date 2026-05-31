package com.afrisanjaya.shipment.billing.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.afrisanjaya.shipment.billing.domain.entity.Wallet;
import com.afrisanjaya.shipment.billing.domain.repository.WalletRepository;
import com.afrisanjaya.shipment.billing.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wallet.sqs", name = "enabled", havingValue = "true")
public class WalletTransactionListener {

    private final SqsAsyncClient sqsAsyncClient;
    private final ObjectMapper objectMapper;
    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;
    private final String walletTransactionQueueUrl;

    @Scheduled(fixedDelayString = "${wallet.sqs.poll-delay-ms:1000}")
    public void poll() {
        List<Message> messages = sqsAsyncClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(walletTransactionQueueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(1)
                .visibilityTimeout(30)
                .build()).join().messages();

        messages.forEach(this::processMessage);
    }

    private void processMessage(Message message) {
        try {
            WalletTransactionMessage walletTransactionMessage =
                    objectMapper.readValue(message.body(), WalletTransactionMessage.class);
            if (ledgerService.hasTransaction(walletTransactionMessage.transactionId())) {
                log.debug("Skipping already-processed wallet transaction: transactionId={}",
                        walletTransactionMessage.transactionId());
                delete(message);
                return;
            }

            Wallet debitWallet = walletRepository.findById(walletTransactionMessage.debitWalletId())
                    .orElseThrow();
            Wallet creditWallet = walletRepository.findById(walletTransactionMessage.creditWalletId())
                    .orElseThrow();

            ledgerService.recordDoubleEntry(
                    walletTransactionMessage.transactionId(),
                    walletTransactionMessage.transactionType(),
                    walletTransactionMessage.amount(),
                    debitWallet,
                    walletTransactionMessage.debitBalanceBefore(),
                    creditWallet,
                    walletTransactionMessage.creditBalanceBefore(),
                    walletTransactionMessage.referenceId(),
                    walletTransactionMessage.description()
            );
            delete(message);
        } catch (Exception e) {
            log.error("Failed to process wallet transaction message: {}", e.getMessage(), e);
        }
    }

    private void delete(Message message) {
        sqsAsyncClient.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(walletTransactionQueueUrl)
                .receiptHandle(message.receiptHandle())
                .build()).join();
    }
}

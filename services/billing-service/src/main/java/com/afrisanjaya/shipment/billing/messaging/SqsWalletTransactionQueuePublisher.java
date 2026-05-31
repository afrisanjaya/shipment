package com.afrisanjaya.shipment.billing.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wallet.sqs", name = "enabled", havingValue = "true")
public class SqsWalletTransactionQueuePublisher implements WalletTransactionQueuePublisher {

    private final SqsAsyncClient sqsAsyncClient;
    private final ObjectMapper objectMapper;
    private final String walletTransactionQueueUrl;

    @Override
    public void publish(WalletTransactionMessage message) {
        try {
            String body = objectMapper.writeValueAsString(message);
            sqsAsyncClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(walletTransactionQueueUrl)
                    .messageGroupId(message.groupId().toString())
                    .messageDeduplicationId(message.deduplicationId().toString())
                    .messageBody(body)
                    .build()).join();
            log.debug("Published wallet transaction to SQS: transactionId={}, groupId={}",
                    message.transactionId(), message.groupId());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize wallet transaction message", e);
        }
    }
}

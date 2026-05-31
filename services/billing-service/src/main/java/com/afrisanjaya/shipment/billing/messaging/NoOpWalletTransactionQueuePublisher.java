package com.afrisanjaya.shipment.billing.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "wallet.sqs", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpWalletTransactionQueuePublisher implements WalletTransactionQueuePublisher {

    @Override
    public void publish(WalletTransactionMessage message) {
        log.debug("Skipping SQS publish because wallet.sqs.enabled=false: transactionId={}", message.transactionId());
    }
}

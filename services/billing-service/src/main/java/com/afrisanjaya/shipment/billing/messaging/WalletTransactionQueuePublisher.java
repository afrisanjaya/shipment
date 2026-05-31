package com.afrisanjaya.shipment.billing.messaging;

public interface WalletTransactionQueuePublisher {

    void publish(WalletTransactionMessage message);
}

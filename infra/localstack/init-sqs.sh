#!/bin/bash

set -e

echo "=== Initializing SQS FIFO queues ==="

awslocal sqs create-queue \
  --queue-name wallet-transactions.fifo \
  --attributes '{
    "FifoQueue": "true",
    "ContentBasedDeduplication": "true",
    "VisibilityTimeout": "60",
    "ReceiveMessageWaitTimeSeconds": "20",
    "MessageRetentionPeriod": "1209600"
  }' 2>/dev/null && echo "  CREATED wallet-transactions.fifo"

awslocal sqs create-queue \
  --queue-name invoice-audit.fifo \
  --attributes '{
    "FifoQueue": "true",
    "ContentBasedDeduplication": "true",
    "VisibilityTimeout": "120",
    "ReceiveMessageWaitTimeSeconds": "20",
    "MessageRetentionPeriod": "604800"
  }' 2>/dev/null && echo "  CREATED invoice-audit.fifo"

echo "=== SQS FIFO initialization complete ==="

awslocal sqs list-queues

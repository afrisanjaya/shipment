package kafka

import (
	"context"
	"log"
	"time"

	"github.com/segmentio/kafka-go"
)

type Consumer struct {
	reader *kafka.Reader
}

func NewConsumer(brokers string, groupID string, topics []string) (*Consumer, error) {
	if len(topics) == 0 {
		log.Println("Warning: no topics provided for Kafka consumer")
	}

	reader := kafka.NewReader(kafka.ReaderConfig{
		Brokers:        []string{brokers},
		GroupID:        groupID,
		Topic:          topics[0],
		MinBytes:       10e3,
		MaxBytes:       10e6,
		CommitInterval: time.Second,
		StartOffset:    kafka.FirstOffset,
	})

	log.Printf("Kafka consumer created for group %s (pure Go)", groupID)
	return &Consumer{reader: reader}, nil
}

func (c *Consumer) Close() {
	if err := c.reader.Close(); err != nil {
		log.Printf("Error closing Kafka consumer: %v", err)
	}
}

func (c *Consumer) ReadMessage(ctx context.Context) ([]byte, error) {
	msg, err := c.reader.ReadMessage(ctx)
	if err != nil {
		return nil, err
	}
	return msg.Value, nil
}

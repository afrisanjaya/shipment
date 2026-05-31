package kafka

import (
	"context"
	"fmt"
	"log"
	"time"

	"github.com/segmentio/kafka-go"
)

type Producer struct {
	writer *kafka.Writer
}

func NewProducer(brokers string) (*Producer, error) {
	writer := &kafka.Writer{
		Addr:         kafka.TCP(brokers),
		Balancer:     &kafka.LeastBytes{},
		RequiredAcks: kafka.RequireAll,
		Async:        false,
		BatchTimeout: 10 * time.Millisecond,
	}

	log.Println("Kafka producer created successfully (pure Go).")
	return &Producer{writer: writer}, nil
}

func (p *Producer) Close() {
	if err := p.writer.Close(); err != nil {
		log.Printf("Error closing Kafka producer: %v", err)
	}
}

func (p *Producer) Publish(topic string, key []byte, value []byte) error {
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	err := p.writer.WriteMessages(ctx, kafka.Message{
		Topic: topic,
		Key:   key,
		Value: value,
	})
	if err != nil {
		return fmt.Errorf("failed to produce message to topic %s: %w", topic, err)
	}
	return nil
}

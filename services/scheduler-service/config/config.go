package config

import (
	"log"

	"github.com/spf13/viper"
)

type Config struct {
	RedisURL     string
	KafkaBrokers string
	HttpPort     string
}

func LoadConfig() (*Config, error) {
	viper.AutomaticEnv()

	viper.SetDefault("REDIS_URL", "redis://localhost:6379/0")
	viper.SetDefault("KAFKA_BROKERS", "localhost:29092")
	viper.SetDefault("HTTP_PORT", "8090")

	cfg := &Config{
		RedisURL:     viper.GetString("REDIS_URL"),
		KafkaBrokers: viper.GetString("KAFKA_BROKERS"),
		HttpPort:     viper.GetString("HTTP_PORT"),
	}

	log.Printf("Loaded config: KAFKA_BROKERS=%s, HTTP_PORT=%s", cfg.KafkaBrokers, cfg.HttpPort)
	return cfg, nil
}

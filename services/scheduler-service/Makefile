
BINARY_NAME=scheduler-service
GO_FILES=$(shell find . -name "*.go")

.PHONY: all build run test clean tidy

all: build

build:
	@echo "Building $(BINARY_NAME)..."
	@go build -o $(BINARY_NAME) main.go

run: build
	@echo "Running $(BINARY_NAME)..."
	@./$(BINARY_NAME)

test:
	@echo "Running tests..."
	@go test ./...

clean:
	@echo "Cleaning up..."
	@rm -f $(BINARY_NAME)

tidy:
	@echo "Tidying up modules..."
	@go mod tidy

build-wsl:
	@echo "Building $(BINARY_NAME) for WSL/Linux..."
	@CGO_ENABLED=1 GOOS=linux go build -o $(BINARY_NAME) main.go

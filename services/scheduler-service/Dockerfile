FROM golang:1.24-alpine AS builder

WORKDIR /app

COPY . .

RUN go mod tidy
RUN go mod download

RUN CGO_ENABLED=0 GOOS=linux go build -o scheduler-service main.go

FROM alpine:latest
WORKDIR /app

COPY --from=builder /app/scheduler-service .

EXPOSE 8090

ENTRYPOINT ["./scheduler-service"]

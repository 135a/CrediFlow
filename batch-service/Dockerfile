FROM golang:1.21-alpine AS builder

WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN go build -o batch-service main.go

FROM alpine:latest
WORKDIR /app
COPY --from=builder /app/batch-service .

ENV TZ=Asia/Shanghai
ENV REPAYMENT_SERVICE_URL=http://apisix:9080
ENV POST_LOAN_SERVICE_URL=http://apisix:9080

ENTRYPOINT ["./batch-service"]

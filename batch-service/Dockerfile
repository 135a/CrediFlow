FROM docker.m.daocloud.io/library/golang:1.21-alpine AS builder

WORKDIR /build
# Configure Go proxy for faster downloads in China
ENV GOPROXY=https://goproxy.cn,direct

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN go mod tidy
# Build the binary
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o batch-service .

FROM docker.m.daocloud.io/library/alpine:3.19
RUN apk add --no-cache ca-certificates tzdata
WORKDIR /app
ENV TZ=Asia/Shanghai

COPY --from=builder /build/batch-service /app/batch-service
RUN chmod +x /app/batch-service
ENV REPAYMENT_SERVICE_URL=http://apisix:9080
ENV POST_LOAN_SERVICE_URL=http://apisix:9080
ENTRYPOINT ["/app/batch-service"]

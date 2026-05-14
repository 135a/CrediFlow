FROM docker.m.daocloud.io/library/golang:1.24-alpine AS builder

WORKDIR /build
ENV GOPROXY=https://goproxy.cn,direct

COPY go.mod ./
RUN go mod download || true

COPY . .
RUN go mod tidy
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o fund-channel-gateway .

FROM docker.m.daocloud.io/library/alpine:3.19
RUN apk add --no-cache ca-certificates tzdata
WORKDIR /app
ENV TZ=Asia/Shanghai
ENV FUND_GATEWAY_CONFIG_PATH=/app/configs/fund-provider.dev.yaml

COPY --from=builder /build/fund-channel-gateway /app/fund-channel-gateway
COPY --from=builder /build/configs /app/configs
RUN chmod +x /app/fund-channel-gateway

EXPOSE 8090
ENTRYPOINT ["/app/fund-channel-gateway"]

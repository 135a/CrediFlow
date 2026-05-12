#!/bin/bash
# APISIX 初始化路由与插件脚本
APISIX_ADMIN="http://127.0.0.1:9080/apisix/admin"
API_KEY="edd1c9f034335f136f87ad84b625c8f1"

echo "Initializing APISIX Upstreams..."

# 1. 创建 Upstream
# App 端 BFF
curl -i $APISIX_ADMIN/upstreams/1 -H "X-API-KEY: $API_KEY" -X PUT -d '
{
    "type": "roundrobin",
    "nodes": {
        "app-bff-service:8091": 1
    }
}'

# 管理端 BFF
curl -i $APISIX_ADMIN/upstreams/2 -H "X-API-KEY: $API_KEY" -X PUT -d '
{
    "type": "roundrobin",
    "nodes": {
        "admin-bff-service:8090": 1
    }
}'

echo -e "\n\nInitializing APISIX Global Rules..."

# 2. 创建全局规则（Global Rules）
# 配置 request-id 注入、访问日志与基础防护
curl -i $APISIX_ADMIN/global_rules/1 -H "X-API-KEY: $API_KEY" -X PUT -d '
{
    "plugins": {
        "request-id": {
            "header_name": "X-Request-Id",
            "include_in_response": true
        },
        "http-logger": {
            "uri": "http://log-collector:5000/logs",
            "batch_max_size": 1000,
            "max_retry_count": 0,
            "retry_delay": 1,
            "buffer_duration": 60,
            "inactive_timeout": 5,
            "name": "http logger",
            "log_format": {
                "host": "$host",
                "client_ip": "$remote_addr",
                "request_id": "$http_x_request_id",
                "method": "$request_method",
                "uri": "$uri",
                "status": "$status",
                "latency": "$request_time",
                "upstream_latency": "$upstream_response_time"
            }
        }
    }
}'

echo -e "\n\nInitializing APISIX Routes..."

# 3. 创建路由与插件配置
# App 端路由（包含 JWT 校验与限流）
curl -i $APISIX_ADMIN/routes/1 -H "X-API-KEY: $API_KEY" -X PUT -d '
{
    "uri": "/api/app/*",
    "upstream_id": 1,
    "plugins": {
        "jwt-auth": {
            "header": "Authorization"
        },
        "limit-req": {
            "rate": 10,
            "burst": 5,
            "rejected_code": 429,
            "key": "remote_addr"
        },
        "ip-restriction": {
            "message": "Your IP is forbidden",
            "blacklist": [
                "192.168.1.100"
            ]
        }
    }
}'

# 管理端路由
curl -i $APISIX_ADMIN/routes/2 -H "X-API-KEY: $API_KEY" -X PUT -d '
{
    "uri": "/api/admin/*",
    "upstream_id": 2,
    "plugins": {
        "jwt-auth": {
            "header": "Authorization"
        },
        "limit-req": {
            "rate": 50,
            "burst": 10,
            "rejected_code": 429,
            "key": "remote_addr"
        }
    }
}'

echo -e "\n\nInitializing APISIX Consumers..."

# 4. 创建 Consumers 并配置 JWT Credentials
# 示例：创建用户凭证
curl -i $APISIX_ADMIN/consumers -H "X-API-KEY: $API_KEY" -X PUT -d '
{
    "username": "crediflow-user",
    "plugins": {
        "jwt-auth": {
            "key": "crediflow-user-key",
            "secret": "CrediFlowUserSecretKey1234567890!"
        }
    }
}'

echo -e "\n\nAPISIX initialization completed!"

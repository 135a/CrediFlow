# 微服务内网调用认证（HMAC 签名）

为防止绕过 APISIX 网关直连内网 HTTP 服务，平台对 **`/api/internal/**`** 采用 **预共享密钥 + 时间戳 + HMAC-SHA256** 的请求级签名；**与 App 用户登录 JWT、会话 Cookie 等终端鉴权无关**（用户态令牌由各服务或 BFF 自行实现，例如 `user-service` 的 `ExternalJwtUtils`）。

## 机制概要

1. **调用方（Java Feign）**：`crediflow-common` 中 `FeignConfig` 注册的 `InternalAuthRequestInterceptor` 对每个出站请求计算  
   `dataToSign = <请求路径> + <毫秒时间戳>`，`signature = Base64(HMAC-SHA256(dataToSign, crediflow.internal.secret))`，并写入请求头：  
   - `X-Timestamp`：毫秒 Unix 时间戳  
   - `X-Internal-Sign`：上述签名
2. **接收方（Java Servlet）**：`com.crediflow.common.filter.InternalAuthFilter`（`@Component`）拦截以 `/api/internal/` 为前缀的路径（白名单除外），校验时间戳防重放（默认 5 分钟）与签名一致性；失败返回 **401**。
3. **白名单**：配置项 `crediflow.internal.public-paths`（逗号分隔）中的路径可豁免平台 `X-Internal-Sign` 强制校验（如人脸厂商异步回调），由厂商签名与边缘防护兜底；详见 OpenSpec `internal-api-security`。
4. **跨语言（Go 等）**：须与 Java 算法一致，例如 `batch-service` 的 `internalsign.Apply`、资金网关中间件文档中的「Mode A」说明。

## 配置

| 配置项 | 含义 |
|--------|------|
| `crediflow.internal.secret` | HMAC 密钥（各环境 MUST 一致且保密） |
| `crediflow.internal.public-paths` | 豁免平台内网签名的内部路径列表 |

## 历史说明

仓库曾存在基于 `X-Internal-Token` 的 JWT Feign 注入与未启用的 `auth` 包过滤器实现，已与生产主链路脱钩并移除；运维侧若在 Nacos 中仍保留 **`crediflow.auth.jwt-secret`**，对当前 `crediflow-common` **无消费者**，可择机清理以免误配。

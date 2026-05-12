# OpenTelemetry 降级策略 (12.3)

在微服务架构初期，为了减轻基础设施负担并控制成本，我们**默认不部署**重量级的 OTel Collector（如 Jaeger 或 Zipkin 集群）。

## 降级路径

1. **链路 ID 透传 (无状态追踪)**
   - 我们在 `crediflow-common` 中实现了 `TraceInterceptor` 与 Feign 的 RequestInterceptor。
   - 这确保了 `X-Trace-Id` 和 `X-Request-Id` 在服务间调用时被无缝透传，并被写入 Mapped Diagnostic Context (MDC)。
   - **结果**: 日志中会包含对应的 trace_id，开发人员可以使用 `grep trace_id` 在集中式日志（如 ELK 或简单的文件合并日志）中串联完整的请求链路。

2. **启用 Collector 的时机**
   - 当微服务实例规模达到 20+，或跨服务性能调优成为核心瓶颈时。
   - **配置方法**:
     在 POM 中引入 `spring-cloud-starter-sleuth` (旧版) 或 `micrometer-tracing-bridge-otel` (Boot 3.x)。
     在 `application.yml` 中配置 endpoint：
     ```yaml
     management:
       tracing:
         sampling:
           probability: 1.0
       zipkin:
         tracing:
           endpoint: "http://otel-collector:9411/api/v2/spans"
     ```

**结论**: 当前阶段通过 JSON 日志中的 MDC 字段进行轻量级链路追踪，完全满足业务调试与错误排查需求。

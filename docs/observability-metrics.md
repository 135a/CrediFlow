# 可观测性与指标暴露规范

所有 Spring Boot 微服务需统一接入 Actuator 与 Prometheus 进行指标暴露。

## 1. 依赖
在 `crediflow-common` 中已统一引入 `spring-boot-starter-actuator` 和 `micrometer-registry-prometheus`。

## 2. 暴露端点
在各个微服务的配置文件 (`application.yml` 或 `bootstrap.yml`) 中，需添加以下配置暴露 Prometheus 端点：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, prometheus
  metrics:
    tags:
      application: ${spring.application.name}
```

## 3. 访问指标
- 健康检查：`http://{host}:{port}/actuator/health`
- Prometheus 指标抓取：`http://{host}:{port}/actuator/prometheus`

通过 Grafana 仪表盘接入 Prometheus 数据源，可实现 JVM、HTTP 请求、线程池等多维度的可视化监控。

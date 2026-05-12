# Nacos 注册与配置中心规范

## 1. 命名空间 (Namespace)
为了实现多环境隔离，Nacos 使用 Namespace 进行环境划分：
- `dev`：开发环境
- `test`：测试环境
- `prod`：生产环境

微服务在 `bootstrap.yml` 中必须显式指定 `namespace`。

## 2. 配置分组 (Group)
统一采用 `DEFAULT_GROUP`，不按服务进行细分 Group（因为 DataId 已包含服务名）。
对于跨服务的全局配置，使用 `GLOBAL_GROUP`。

## 3. 应用名与 DataId 约定
- 应用名 (`spring.application.name`) 必须与代码仓库模块名一致，如 `user-service`。
- Nacos 配置文件的 DataId 规则：`${spring.application.name}-${spring.profiles.active}.${file-extension}`
  - 例：`user-service-dev.yml`

## 4. 接入示例
`bootstrap.yml` 模板：
```yaml
spring:
  application:
    name: example-service
  profiles:
    active: dev
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:dev}
      config:
        server-addr: ${NACOS_SERVER_ADDR:127.0.0.1:8848}
        namespace: ${NACOS_NAMESPACE:dev}
        file-extension: yml
        shared-configs:
          - data-id: common-config.yml
            group: GLOBAL_GROUP
            refresh: true
```

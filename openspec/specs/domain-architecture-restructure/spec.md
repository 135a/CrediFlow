# Capability: domain-architecture-restructure

## Purpose
TBD: 系统按照业务域（Domain）重新组织项目物理结构与构建配置，废弃按语言分层的方式，以提升域内高内聚性与多语言协作效率。

## Requirements

### Requirement: 项目目录结构按业务域重组
系统 MUST 废除原本按语言（`java`、`go`、`python`）划分顶层目录的架构，改为按核心业务域（Domain）划分，包括但不限于 `user`、`loan`、`credit`、`fund`、`contract`、`post-loan`、`bff` 与 `crediflow-common`。各个语言实现的微服务组件 MUST 归置于其对应的业务域目录下。

#### Scenario: 跨语言服务归于同域
- **WHEN** 资金流水的业务包括 Java 服务 `fund-flow-service` 和 Go 网关 `fund-channel-gateway`
- **THEN** 这两个服务 MUST 并行存放于项目根目录的 `fund/` 业务域目录下，而不再被隔离在不同语言的顶层目录中

#### Scenario: 公共依赖提取
- **WHEN** 所有的微服务都依赖一些基础公共组件
- **THEN** 公共组件库（如原 `crediflow-common`） MUST 提升至项目顶层级别的 `crediflow-common/` 目录，供各域下的服务引用

### Requirement: 构建系统配置与依赖更新
系统 MUST 确保在物理目录重构后，所有的构建配置文件与运行环境（Maven/Go/Docker）保持绝对一致并能够成功执行。

#### Scenario: Maven 聚合配置适配
- **WHEN** 原先在 `java/` 目录下的 Spring Boot 服务转移到了根目录的特定域下
- **THEN** 全局或域级别的 `pom.xml` MUST 同步更新其 `<module>` 路径以准确指向新的位置，且各子模块的 `<parent>` 引用路径 MUST 正确可用

#### Scenario: Docker 化部署适配
- **WHEN** 使用 Docker Compose 对系统进行整体打包运行
- **THEN** `docker-compose.yml` 中的所有相关 `build.context` MUST 修改为业务域层级的新路径（例如 `./fund/fund-channel-gateway`）

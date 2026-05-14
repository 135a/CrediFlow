## Context

当前代码库按照 `java/`、`go/`、`python/` 顶层目录结构划分，这种语言维度的隔离导致了系统架构呈现“横向分层”的错觉，而在实际业务迭代中，功能通常是跨语言端到端交付的。例如，一个风控需求可能同时涉及 Java (`credit-risk-service`) 和 Python (`data_agent`)。继续按照语言划分会导致微服务上下文割裂，不符合领域驱动设计 (DDD) 按业务领域内聚的理念。

## Goals / Non-Goals

**Goals:**
- 将当前所有的微服务与组件从按编程语言分类，重构为按业务领域（Business Domain）进行归类。
- 在项目根目录建立业务领域的顶层目录（如 `user`、`loan`、`credit`、`fund`、`contract` 等），每种语言实现的相关组件作为子目录放入对应的领域目录中。
- 更新并适配现有的所有构建配置文件（Maven `pom.xml`、Go `go.mod` 等），以及 `docker-compose.yml` 和 `Dockerfile`，确保所有服务在目录重构后能正常编译与启动。

**Non-Goals:**
- 不涉及任何既有业务逻辑的修改或优化。
- 不引入新的微服务，不改变现有微服务之间的数据交互协议和调用关系（即，只动位置，不动逻辑）。
- 不改变现有的外部基础设施依赖（如 Nacos, Redis, MySQL 等）。

## Decisions

1. **统一业务领域作为顶层目录 (Top-Level Domain Folders)**:
   将项目原有的 `java/`、`go/`、`python/` 删除，在根目录直接建立如下核心领域：
   - `crediflow-common/`: 公共组件域（原 Java 下的 `crediflow-common`）。
   - `bff/`: 聚合层域（包含 `admin-bff-service` 和 `app-bff-service`）。
   - `user/`: 用户域（包含 `user-service`）。
   - `loan/`: 借款域（包含 `loan-application-service`）。
   - `credit/`: 信用与风控域（包含 `credit-risk-service` 和 Python 的 `data_agent`）。
   - `fund/`: 资金账务域（包含 Java 的 `fund-flow-service` 和 Go 的 `fund-channel-gateway`）。
   - `contract/`: 合同域（包含 `contract-service`）。
   - `post-loan/`: 贷后域（包含 `post-loan-service`）。

2. **构建系统的路径适配方案**:
   - **Java/Maven**: 将原来位于 `java/pom.xml` 的父级 POM 移动到根目录（或保留一个统领全局的构建模块），然后更新 `<modules>` 下的路径，如从 `<module>user-service</module>` 修改为 `<module>user/user-service</module>` 等。
   - **Go**: `go/fund-channel-gateway` 移动到 `fund/fund-channel-gateway`，确保 `go.mod` 在对应的子模块目录下能正常运行和构建。
   - **Python**: `python/data_agent` 移动到 `credit/data_agent`。

3. **容器化与部署适配**:
   - `docker-compose.yml` 中的每个 `service` 的 `build.context` 需要指向新的路径（如 `context: ./fund/fund-channel-gateway`）。
   - 由于 Java 服务的构建上下文如果依赖父 POM，可能需要将 `docker-compose` 中的上下文设置为根目录并在 `Dockerfile` 里提供相对路径；但在本项目中，通常保持使用原本的 Spring Boot 插件构建或单模块 `Dockerfile`。我们将针对目录变动，统一修正这些路径。

## Risks / Trade-offs

1. **构建依赖断裂风险**
   - *Risk*: 移动目录后，Maven 子模块可能找不到 Parent POM 导致构建失败。
   - *Mitigation*: 修改所有 Java 服务的 `pom.xml`，通过 `<relativePath>` 正确指向移动后的 parent 相对路径，并本地完整执行 `mvn clean install` 确保构建成功。
2. **Docker Compose 构建失败风险**
   - *Risk*: `Dockerfile` 中复制依赖或文件时的相对路径失效。
   - *Mitigation*: 逐一调整 `docker-compose.yml` 中的上下文路径和构建参数，并用 `docker-compose build` 进行全量干跑验证。
3. **测试脚本失效风险**
   - *Risk*: `benchmark_suite.py` 或其他自动化脚本引用了原有的相对路径，导致脚本执行报错。
   - *Mitigation*: 同步修复这些引用路径。

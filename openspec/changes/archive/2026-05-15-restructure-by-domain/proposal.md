## Why

随着项目复杂度的提升和微服务的增多，现有代码库采用按编程语言（`java`、`go`、`python`）划分顶层目录的模式，导致了以下痛点：
1. **领域内聚性差**：同一个业务域（如核心风控、资金放款流等）可能由多种语言实现的不同组件构成，按语言划分使得同一业务域的代码被割裂在不同的目录中，降低了代码的可维护性和上下文关联性。
2. **微服务边界模糊**：开发者在浏览代码时，无法直观地看到系统具有哪些业务模块，而是只能看到语言分类。
3. **架构演进受限**：在目前的 Agentic 架构（Java/Python 混编）演进中，将 Python Data Agent 和 Java 核心服务独立存放不利于将它们作为整体域服务进行部署和版本管理。

因此，按照业务域（Domain）和微服务边界对项目进行重组（不再按语言分类），是实现更规范的领域驱动设计（DDD）和提升团队协作效率的必由之路。

## What Changes

本次重构将进行一次彻底的代码库目录结构洗牌：
1. **废除语言维度的顶层目录**：删除现有的 `java/`、`go/`、`python/` 顶层分类。
2. **按业务域重组模块**：根据现有的业务边界，在项目根目录下建立各领域的目录结构。例如：
   - `common/` 或 `crediflow-common/`: 公共基础组件和工具包（原 Java 侧的 `crediflow-common`）。
   - `user/`: 包含用户服务（原 `user-service`）。
   - `loan/`: 包含借款申请服务（原 `loan-application-service`）。
   - `credit/`: 包含风控与授信服务（原 `credit-risk-service`，并可能将相关的 Python Data Agent 整合进去或作为独立子模块）。
   - `fund/`: 包含资金与账务流水服务（原 Java 的 `fund-flow-service` 和 Go 的 `fund-channel-gateway`）。
   - `contract/`: 包含合同服务（原 `contract-service`）。
   - `post-loan/`: 包含贷后与催收服务（原 `post-loan-service`）。
   - `bff/`: 包含各类前端聚合层（原 `admin-bff-service` 和 `app-bff-service`）。
3. **调整构建与 CI/CD 配置**：重组目录后，对应的 Maven `pom.xml`（父子模块关联路径）、Go 模块路径、Docker 构建脚本（Dockerfile 及 Docker Compose 路径）均需要做全面适配和调整。

## Capabilities

### New Capabilities

- `domain-architecture-restructure`: 定义基于业务域和微服务边界的代码库组织架构标准、跨语言协作模块的新目录规范及模块间依赖边界。

### Modified Capabilities

由于本次改动为纯架构级的目录重构，不涉及具体的业务逻辑修改，因此没有已有的业务 Capabilities（如借款申请、人工审核等）需求层面的变更。

## Impact

- **代码库结构**：几乎所有源码文件的绝对路径都会发生变化。
- **构建系统**：Maven 的 `<module>` 聚合配置路径、Go 的 `go.mod`。
- **部署发布**：`docker-compose.yml` 中的 `build.context` 路径以及所有的 `Dockerfile` 路径都会受影响。
- **测试体系**：如 `benchmark_suite.py` 等测试脚本的引用路径与运行环境。

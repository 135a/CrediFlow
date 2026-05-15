## 1. 建立顶层领域结构与迁移公共组件

- [x] 1.1 在项目根目录创建业务域文件夹：`user/`, `loan/`, `credit/`, `fund/`, `contract/`, `post-loan/`, `bff/`。
- [x] 1.2 将 `java/crediflow-common` 移动至项目根目录的 `crediflow-common/`，并更新其 `pom.xml` 中的相对路径。
- [x] 1.3 提取全局父 `pom.xml`（原 `java/pom.xml`）至根目录，更新所有 `<module>` 声明为新的相对路径。

## 2. 迁移并重组 Java 微服务

- [x] 2.1 将 `java/user-service` 移动至 `user/user-service`，更新其 `pom.xml` 的 `<parent>` 相对路径。
- [x] 2.2 将 `java/loan-application-service` 移动至 `loan/loan-application-service`，更新其 `pom.xml` 的 `<parent>` 相对路径。
- [x] 2.3 将 `java/credit-risk-service` 移动至 `credit/credit-risk-service`，更新其 `pom.xml` 的 `<parent>` 相对路径。
- [x] 2.4 将 `java/fund-flow-service` 移动至 `fund/fund-flow-service`，更新其 `pom.xml` 的 `<parent>` 相对路径。
- [x] 2.5 将 `java/contract-service` 移动至 `contract/contract-service`，更新其 `pom.xml` 的 `<parent>` 相对路径。
- [x] 2.6 将 `java/post-loan-service` 移动至 `post-loan/post-loan-service`，更新其 `pom.xml` 的 `<parent>` 相对路径。
- [x] 2.7 将 `java/admin-bff-service` 和 `java/app-bff-service` 移动至 `bff/` 目录下，并更新其 `pom.xml`。
- [x] 2.8 验证 Java 侧依赖：在根目录下执行 `mvn clean install -DskipTests`，确保所有 Java 模块可成功编译。

## 3. 迁移并重组 Go 与 Python 微服务

- [x] 3.1 将 `go/fund-channel-gateway` 移动至 `fund/fund-channel-gateway`，检查并确保 `go.mod` 在新路径下可以正常执行 `go build`。
- [x] 3.2 将 `python/data_agent` 移动至 `credit/data_agent`，并验证其 Python 依赖和执行路径是否仍旧有效。

## 4. 调整构建与部署配置

- [x] 4.1 全面排查并修改根目录下的 `docker-compose.yml`，将各服务的 `build.context` 替换为迁移后的实际域路径（如从 `./java/user-service` 改为 `./user/user-service`）。
- [x] 4.2 排查并修改各个语言对应 `Dockerfile` 内部的 `COPY` 指令相对路径，以适配新的项目目录结构。
- [x] 4.3 修复外围自动化脚本与测试文件（如 `Test/benchmark_suite.py`），修正其中对微服务路径的硬编码引用。
- [x] 4.4 执行端到端构建 `docker-compose build` 并验证全套系统是否能成功启动联调。
- [x] 4.5 删除已清空的废弃顶层目录 `java/`, `go/`, `python/`。

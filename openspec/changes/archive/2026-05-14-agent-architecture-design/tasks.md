## 1. 架构文档与指引下发

- [x] 1.1 在项目代码的适当位置（如 `docs/` 目录或项目根目录的 README）更新架构说明，同步 5 大智能体（用户、风控、定价、合约、贷后）的划分与边界给研发团队。
- [x] 1.2 编写一份针对 Python 与 Java 跨 Agent 调用的规范示例（可记录为 Markdown 文件），明确定价/风控 Python 接口的入参出参标准。

## 2. 跨域通信与流转基建准备 (Java Common)

- [x] 2.1 在 `common` 或 `common-web` 模块中，定义供内部 Agent 跨域调用使用的 HTTP Header 常量（如 `X-Agent-Source`, `X-Trace-Id`）。
- [x] 2.2 在微服务的统一拦截器（或 Feign 拦截器）中，添加逻辑：发起 HTTP 请求时自动向外部注入 `X-Trace-Id`，以便于跨 Java-Python 链路的追踪。
- [x] 2.3 在 `common` 模块中，新增一个用于标识“最终一致性”或“异步补偿”的业务异常基类（如 `AgentAsyncRetryException`），统一规范长流程中断时的异常抛出。

## 3. 异构服务对接点梳理

- [x] 3.1 在 Python 端的网关或核心入口模块（如果已有代码雏形）中，添加或标记 TODO 以实现接收并日志打印 `X-Trace-Id` 的请求上下文拦截器。
- [x] 3.2 检查现有基于 Feign 或 RestTemplate 的 HTTP 调用配置，确保其符合不硬编码 IP 而是依赖统一路由网关的架构约束，必要时剥离硬编码的微服务地址。

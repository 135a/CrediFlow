## 1. 物理删除失效注解类

- [x] 1.1 全局搜索并删除微服务中所有对 `@IgnoreAuth` 注解的使用（包括类/方法上的标记和 import 语句）
- [x] 1.2 全局搜索并删除微服务中所有对 `@Inner` 注解的使用（包括类/方法上的标记和 import 语句）
- [x] 1.3 从 `crediflow-common` 中物理删除 `IgnoreAuth.java` 和 `Inner.java` 源码文件

## 2. 沉淀架构哲学文档

- [x] 2.1 在 `docs/` 目录下创建 `auth-architecture-philosophy.md` 文件
- [x] 2.2 撰写内容，明确论述在分布式零信任网关架构下，采用“路径规范控制（如 `/api/internal/`）与 APISIX 声明式放行”全面取代“微服务内 AOP/Filter 注解式安全”的核心逻辑及优劣取舍

## 3. 编译验证

- [x] 3.1 运行全局 `mvn clean compile -DskipTests` 验证依赖了这些注解的业务服务（如 user-service, fund-flow-service 等）是否均能通过编译

# 实名 Mock 与生产 Profile 判定（运维检查清单）

## 生产判定规则（与代码一致）

- `RealnameMockSafetyInitializer` 与 `RealnameProperties` 约定：**当前激活的 Spring profile 名称**（不区分大小写）若命中以下任一集合，则视为**生产**：
  - 固定集合：`prod`、`production`
  - 扩展集合：环境变量 / Nacos 中的 **`crediflow.realname.prod-profiles`**（逗号分隔的附加 profile 名，例如 `prod,release`）
- 仅当**判定为生产**且 **`crediflow.realname.mock-success=true`** 时，应用在启动阶段**直接失败**（抛出 `IllegalStateException`），禁止进程对外服务。

## 发布前检查

1. 生产 Nacos / 环境变量中确认 **`crediflow.realname.mock-success` 为 false 或未设置**。
2. 确认生产 **`crediflow.realname.base-url`、`app-key`、`app-secret`** 已配置且非仓库默认值。
3. 确认 **`crediflow.realname.idempotency-salt`** 为独立随机密钥，与测试环境隔离。

## 回滚

关闭新实例或回滚镜像即可；DB 新增列为可空，旧版本服务可不读写字段。

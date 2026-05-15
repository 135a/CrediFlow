## Context

在 `ContractInternalController` 中，暴露了这样一个内网 API `GET /api/internal/contract/credit-status`。其目前的实现是：
直接在控制器内部 `new LambdaQueryWrapper<>()`，去 `loanContractService.getOne()` 取出数据，然后在控制器内组装 `Map` 作为业务返回体。

这种反模式在企业级开发中有以下问题：
1. **测试困难**：如果想单独针对“获取最新合同状态”这一业务进行单元测试，需要 mock 整个 HTTP 层，而实际上这只是纯业务逻辑。
2. **逻辑不可复用**：如果另外一个服务（甚至是定时任务、MQ 消费者）需要获取此状态，它们将无法复用这段写在 Controller 里的查询与数据转换逻辑。
3. **架构腐化**：打破了控制层与服务层的界限，如果日后查询逻辑变复杂（例如加入缓存），会导致控制器膨胀。

## Goals / Non-Goals

**Goals**:
- 将特定的 `LambdaQueryWrapper` 以及相关返回状态组装移动到 `LoanContractServiceImpl`。
- Controller 层仅需一行代码调用该 Service。

## Decisions

- **决议 1: 在 `LoanContractService` 中新增方法**
  新增 `Map<String, Object> getLatestCreditContractStatus(Long userId)`，将原有逻辑平移。
  
- **决议 2: Controller 瘦身**
  `ContractInternalController` 内仅仅 `return Result.success(loanContractService.getLatestCreditContractStatus(userId));`。

## Risks / Trade-offs

本次重构风险极低，仅为代码层面的纯逻辑物理移动。

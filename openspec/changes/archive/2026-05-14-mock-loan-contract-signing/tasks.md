## 1. 模拟合同生成与查询改造 (LoanContractServiceImpl)

- [x] 1.1 在 `LoanContractServiceImpl.generateContract` 方法内添加 `TODO: 后续接入 Python 服务渲染 PDF 模板，并存储至 OSS` 注释。
- [x] 1.2 确认 `generateContract` 插入的 `cf_loan_contract` 记录状态为 `INIT`。
- [x] 1.3 确认并完善 `getContractLink` 查询方法，当前阶段返回 Mock 的 PDF 链接（如 `dummy.pdf`），并添加 `TODO: 后续替换为从 OSS 获取真实的合同下载链接或预览 Token`。

## 2. 模拟签署流程与借据防重改造 (LoanContractServiceImpl)

- [x] 2.1 修改 `LoanContractServiceImpl.signAndGenerateContract` 方法，添加防重复签署拦截逻辑：若通过查询发现状态已经是 `SIGNED`，应直接返回成功（幂等），防止重复执行。
- [x] 2.2 在 `signAndGenerateContract` 的签名更新逻辑处，添加 `TODO: 此处为同步模拟，后续将替换为：前端跳电子签 SDK -> 后端接收三方签章异步回调更新 SIGNED 状态`。
- [x] 2.3 确保在 `signAndGenerateContract` 状态更新为 `SIGNED` 后，同步调用 `generateReceiptAndPlan`（或通过事件触发），以打通借据创建与放款。

## 3. 接口层确认 (Controller)

- [x] 3.1 确认 `LoanContractController` (如果存在) 中是否已暴露 `/api/app/contract/sign` (或其他映射到 `signAndGenerateContract`) 的路由；若缺失则补充。

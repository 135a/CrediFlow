## 1. 清理冗余代码

- [x] 1.1 删除 `batch/scheduler-go/` 整个目录。

## 2. DTO 设计与定义

- [x] 2.1 在 `post-loan-service` 内的 dto 包创建 `PenaltyCalculateRequest`，包含 `calcDate` 和 `triggerSource` 字段。
- [x] 2.2 在 `user-service` 内的 dto 包创建 `RepaymentReminderRequest`，包含 `dueDate`，`reminderType` 和 `triggerSource` 字段。
- [x] 2.3 在 `user-service` 内的 dto 包创建 `BatchPushRequest`，包含 `batchTime`，`types` (List) 和 `triggerSource` 字段。

## 3. post-loan-service 端点补齐

- [x] 3.1 修改 `PostLoanController.java`，将 `triggerOverdueInspection` 重命名并映射为 `POST /api/internal/post-loan/overdue/scan`。
- [x] 3.2 修改 `PostLoanController.java`，新增端点 `POST /api/internal/post-loan/penalty/calculate`，接收 `PenaltyCalculateRequest` 并打印日志，返回 Result.success。

## 4. user-service 端点补齐

- [x] 4.1 在 `user-service` 创建 `UserNotifyInternalController.java` (映射为 `/api/internal/user/notify`)。
- [x] 4.2 在 `UserNotifyInternalController.java` 新增端点 `POST /repayment-reminder`，接收 `RepaymentReminderRequest` 并打印日志，返回 Result.success。
- [x] 4.3 在 `UserNotifyInternalController.java` 新增端点 `POST /batch-push`，接收 `BatchPushRequest` 并打印日志，返回 Result.success。

## 5. 编译验证

- [x] 5.1 运行 `mvn clean compile` 以确保所有新建的类和修改能够正常编译。

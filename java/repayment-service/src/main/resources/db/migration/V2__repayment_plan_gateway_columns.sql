-- fund-provider-go-gateway 任务 8.x：为还款计划表补充网关相关列。
--
-- provider_id        ：业务侧选择的资金方标识；空 => 网关回落到 Nacos defaultProviderId
-- gateway_request_id ：Go fund-channel-gateway 同步受理号（SUBMITTED 时回填）
-- provider_txn_no    ：资金方流水号（终态 REPAYMENT_SETTLED_EVENT 回填）
-- submitted_at       ：受理时间，配合 SUBMITTED 中间态做超时巡检与运营查询

ALTER TABLE cf_repayment_plan
    ADD COLUMN provider_id VARCHAR(64) NULL COMMENT '资金方标识 (混合路由)',
    ADD COLUMN gateway_request_id VARCHAR(64) NULL COMMENT 'fund-channel-gateway 受理号',
    ADD COLUMN provider_txn_no VARCHAR(128) NULL COMMENT '资金方侧流水号 (终态回填)',
    ADD COLUMN submitted_at TIMESTAMP NULL COMMENT '受理时间, 用于中间态巡检';

ALTER TABLE cf_repayment_plan
    ADD INDEX idx_gateway_request_id (gateway_request_id);

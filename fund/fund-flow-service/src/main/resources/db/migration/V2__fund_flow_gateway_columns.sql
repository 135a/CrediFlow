ALTER TABLE cf_fund_flow
    ADD COLUMN provider_id VARCHAR(64) NULL COMMENT '资金方标识' AFTER third_party_trade_no,
    ADD COLUMN gateway_request_id VARCHAR(128) NULL COMMENT 'Go资金网关受理请求号' AFTER provider_id,
    ADD COLUMN provider_txn_no VARCHAR(128) NULL COMMENT '资金方流水号' AFTER gateway_request_id,
    ADD COLUMN payload_digest VARCHAR(128) NULL COMMENT '桥接回调报文SHA-256摘要' AFTER provider_txn_no;

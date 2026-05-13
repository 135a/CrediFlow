package com.crediflow.users.messaging;

import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 演示消费者幂等：写入 cf_mq_consumer_processed（任务 4.3 骨架）。
 */
@Component
@ConditionalOnProperty(name = "crediflow.demo-mq-consumer", havingValue = "true")
@RocketMQMessageListener(
        topic = "crediflow.loan.disbursed",
        consumerGroup = "user-service-demo-consumer"
)
public class DemoLoanDisbursedListener implements RocketMQListener<MessageExt> {

    public static final String CONSUMER_GROUP = "user-service-demo-consumer";

    private final JdbcTemplate jdbcTemplate;

    public DemoLoanDisbursedListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void onMessage(MessageExt message) {
        String msgId = message.getMsgId();
        int updated = jdbcTemplate.update(
                "INSERT IGNORE INTO cf_mq_consumer_processed(msg_id, consumer_group, processed_at) VALUES (?,?,?)",
                msgId,
                CONSUMER_GROUP,
                Instant.now()
        );
        if (updated == 0) {
            return;
        }
        // 业务处理占位
    }
}

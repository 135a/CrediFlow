package com.crediflow.application.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.crediflow.application.entity.LocalMessage;
import com.crediflow.application.mapper.LocalMessageMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class LocalMessageScheduler {

    @Autowired
    private LocalMessageMapper localMessageMapper;

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Scheduled(fixedDelay = 5000) // 每5秒轮询一次
    public void processNewMessages() {
        QueryWrapper<LocalMessage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", "NEW").last("LIMIT 100");
        List<LocalMessage> messages = localMessageMapper.selectList(queryWrapper);

        for (LocalMessage msg : messages) {
            try {
                rocketMQTemplate.send(msg.getTopic() + ":" + msg.getTag(), MessageBuilder.withPayload(msg.getPayload()).build());
                msg.setStatus("PUBLISHED");
                msg.setUpdatedAt(new Date());
                localMessageMapper.updateById(msg);
            } catch (Exception e) {
                // 如果发送失败，重试次数加一
                msg.setRetryCount(msg.getRetryCount() + 1);
                if (msg.getRetryCount() >= 3) {
                    msg.setStatus("FAILED");
                }
                msg.setUpdatedAt(new Date());
                localMessageMapper.updateById(msg);
            }
        }
    }
}

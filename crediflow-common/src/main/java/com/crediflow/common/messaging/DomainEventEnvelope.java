package com.crediflow.common.messaging;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * RocketMQ 等领域事件通用信封（任务 4.1）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DomainEventEnvelope(
        String eventId,
        Instant occurredAt,
        int schemaVersion,
        String eventType,
        Object payload
) {
    public static DomainEventEnvelope create(String eventType, int schemaVersion, Object payload) {
        return new DomainEventEnvelope(
                UUID.randomUUID().toString(),
                Instant.now(),
                schemaVersion,
                eventType,
                payload
        );
    }
}

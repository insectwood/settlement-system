package com.settleup.common.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Common EventEnvelope in All domains.
 * Schema: docs/event-design-v0.1.md §5
 */
public record EventEnvelope<T>(
        UUID eventId, String eventType, int schemaVersion, OffsetDateTime occurredAt, UUID correlationId, T payload) {}

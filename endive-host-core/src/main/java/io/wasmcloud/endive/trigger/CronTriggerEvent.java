package io.wasmcloud.endive.trigger;

import java.time.Instant;

public record CronTriggerEvent(Instant timestamp) implements TriggerEvent {
    public CronTriggerEvent() {
        this(Instant.now());
    }
}

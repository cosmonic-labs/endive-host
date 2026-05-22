package io.wasmcloud.endive.trigger;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = CronTriggerEvent.class, name = "cron"),
        @JsonSubTypes.Type(value = NatsMessageEvent.class, name = "nats"),
        @JsonSubTypes.Type(value = HttpTriggerEvent.class, name = "http")
})
public sealed interface TriggerEvent permits CronTriggerEvent, NatsMessageEvent, HttpTriggerEvent {
}

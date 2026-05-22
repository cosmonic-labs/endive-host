package io.wasmcloud.endive.trigger;

public record NatsMessageEvent(String subject, byte[] payload) implements TriggerEvent {
}

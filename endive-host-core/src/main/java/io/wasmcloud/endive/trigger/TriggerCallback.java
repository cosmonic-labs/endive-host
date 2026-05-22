package io.wasmcloud.endive.trigger;

@FunctionalInterface
public interface TriggerCallback {
    byte[] onTrigger(TriggerEvent event);
}

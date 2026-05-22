package io.wasmcloud.endive.trigger;

public interface TriggerSource {
    String id();
    void start(TriggerCallback callback);
    void stop();
}

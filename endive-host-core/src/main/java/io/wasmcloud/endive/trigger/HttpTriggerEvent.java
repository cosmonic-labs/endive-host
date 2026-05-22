package io.wasmcloud.endive.trigger;

import java.util.Map;

public record HttpTriggerEvent(
        String method,
        String path,
        Map<String, String> headers,
        byte[] body
) implements TriggerEvent {
}

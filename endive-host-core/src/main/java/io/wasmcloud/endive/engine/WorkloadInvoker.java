package io.wasmcloud.endive.engine;

import io.wasmcloud.endive.trigger.TriggerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class WorkloadInvoker {
    private static final Logger LOG = LoggerFactory.getLogger(WorkloadInvoker.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WasmEngine engine;

    public WorkloadInvoker(WasmEngine engine) {
        this.engine = engine;
    }

    public byte[] invoke(byte[] wasmBytes, TriggerEvent event, Map<String, String> env) {
        try {
            var module = engine.loadModule(wasmBytes);
            byte[] stdin = serializeEvent(event);
            return module.invoke(stdin, env);
        } catch (Exception e) {
            LOG.error("Failed to invoke workload", e);
            return new byte[0];
        }
    }

    private byte[] serializeEvent(TriggerEvent event) {
        try {
            return MAPPER.writeValueAsBytes(event);
        } catch (Exception e) {
            LOG.error("Failed to serialize trigger event", e);
            return new byte[0];
        }
    }
}

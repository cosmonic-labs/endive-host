package io.wasmcloud.endive.host;

import io.wasmcloud.endive.engine.WasmModule;
import io.wasmcloud.endive.trigger.TriggerSource;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ManagedWorkload {
    private final String workloadId;
    private final byte[] wasmBytes;
    private volatile WorkloadState state;
    private volatile String message;
    private final List<TriggerSource> triggers;
    private volatile WasmModule module;

    public ManagedWorkload(String workloadId, byte[] wasmBytes) {
        this.workloadId = workloadId;
        this.wasmBytes = wasmBytes;
        this.state = WorkloadState.STARTING;
        this.message = "";
        this.triggers = new CopyOnWriteArrayList<>();
    }

    public String workloadId() { return workloadId; }
    public byte[] wasmBytes() { return wasmBytes; }
    public WorkloadState state() { return state; }
    public String message() { return message; }
    public List<TriggerSource> triggers() { return triggers; }
    public WasmModule module() { return module; }

    public void setState(WorkloadState state) { this.state = state; }
    public void setMessage(String message) { this.message = message; }
    public void setModule(WasmModule module) { this.module = module; }

    public void addTrigger(TriggerSource trigger) {
        triggers.add(trigger);
    }
}

package io.wasmcloud.endive.host;

import io.wasmcloud.endive.proto.WorkloadProto;

public enum WorkloadState {
    STARTING(WorkloadProto.WorkloadState.WORKLOAD_STATE_STARTING),
    RUNNING(WorkloadProto.WorkloadState.WORKLOAD_STATE_RUNNING),
    COMPLETED(WorkloadProto.WorkloadState.WORKLOAD_STATE_COMPLETED),
    STOPPING(WorkloadProto.WorkloadState.WORKLOAD_STATE_STOPPING),
    ERROR(WorkloadProto.WorkloadState.WORKLOAD_STATE_ERROR),
    NOT_FOUND(WorkloadProto.WorkloadState.WORKLOAD_STATE_NOT_FOUND);

    private final WorkloadProto.WorkloadState protoState;

    WorkloadState(WorkloadProto.WorkloadState protoState) {
        this.protoState = protoState;
    }

    public WorkloadProto.WorkloadState toProto() {
        return protoState;
    }

    public static WorkloadState fromProto(WorkloadProto.WorkloadState proto) {
        return switch (proto) {
            case WORKLOAD_STATE_STARTING -> STARTING;
            case WORKLOAD_STATE_RUNNING -> RUNNING;
            case WORKLOAD_STATE_COMPLETED -> COMPLETED;
            case WORKLOAD_STATE_STOPPING -> STOPPING;
            case WORKLOAD_STATE_ERROR -> ERROR;
            case WORKLOAD_STATE_NOT_FOUND -> NOT_FOUND;
            default -> NOT_FOUND;
        };
    }
}

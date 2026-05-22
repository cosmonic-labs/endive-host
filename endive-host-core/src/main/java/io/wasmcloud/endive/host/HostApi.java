package io.wasmcloud.endive.host;

import io.wasmcloud.endive.proto.HostHeartbeatProto.HostHeartbeat;
import io.wasmcloud.endive.proto.WorkloadServiceProto.*;

public interface HostApi {
    HostHeartbeat heartbeat();
    WorkloadStartResponse workloadStart(WorkloadStartRequest request);
    WorkloadStatusResponse workloadStatus(WorkloadStatusRequest request);
    WorkloadStopResponse workloadStop(WorkloadStopRequest request);
}

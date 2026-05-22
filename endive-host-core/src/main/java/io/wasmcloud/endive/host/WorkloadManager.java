package io.wasmcloud.endive.host;

import io.wasmcloud.endive.proto.WorkloadProto;
import io.wasmcloud.endive.proto.WorkloadServiceProto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

public class WorkloadManager {
    private static final Logger LOG = LoggerFactory.getLogger(WorkloadManager.class);
    private final ConcurrentHashMap<String, ManagedWorkload> workloads = new ConcurrentHashMap<>();

    public WorkloadStartResponse startWorkload(String workloadId, byte[] wasmBytes) {
        if (workloads.containsKey(workloadId)) {
            LOG.warn("Workload {} already exists", workloadId);
            return WorkloadStartResponse.newBuilder()
                    .setWorkloadStatus(WorkloadStatus.newBuilder()
                            .setWorkloadId(workloadId)
                            .setWorkloadState(WorkloadProto.WorkloadState.WORKLOAD_STATE_ERROR)
                            .setMessage("workload already exists")
                            .build())
                    .build();
        }

        var managed = new ManagedWorkload(workloadId, wasmBytes);
        workloads.put(workloadId, managed);
        LOG.info("Workload {} registered", workloadId);

        return WorkloadStartResponse.newBuilder()
                .setWorkloadStatus(WorkloadStatus.newBuilder()
                        .setWorkloadId(workloadId)
                        .setWorkloadState(WorkloadProto.WorkloadState.WORKLOAD_STATE_STARTING)
                        .setMessage("workload starting")
                        .build())
                .build();
    }

    public WorkloadStatusResponse statusWorkload(String workloadId) {
        var managed = workloads.get(workloadId);
        if (managed == null) {
            return WorkloadStatusResponse.newBuilder()
                    .setWorkloadStatus(WorkloadStatus.newBuilder()
                            .setWorkloadId(workloadId)
                            .setWorkloadState(WorkloadProto.WorkloadState.WORKLOAD_STATE_NOT_FOUND)
                            .setMessage("workload not found")
                            .build())
                    .build();
        }

        return WorkloadStatusResponse.newBuilder()
                .setWorkloadStatus(WorkloadStatus.newBuilder()
                        .setWorkloadId(workloadId)
                        .setWorkloadState(managed.state().toProto())
                        .setMessage(managed.message())
                        .build())
                .build();
    }

    public WorkloadStopResponse stopWorkload(String workloadId) {
        var managed = workloads.get(workloadId);
        if (managed == null) {
            return WorkloadStopResponse.newBuilder()
                    .setWorkloadStatus(WorkloadStatus.newBuilder()
                            .setWorkloadId(workloadId)
                            .setWorkloadState(WorkloadProto.WorkloadState.WORKLOAD_STATE_NOT_FOUND)
                            .setMessage("workload not found")
                            .build())
                    .build();
        }

        managed.setState(WorkloadState.STOPPING);
        for (var trigger : managed.triggers()) {
            try {
                trigger.stop();
            } catch (Exception e) {
                LOG.error("Error stopping trigger {} for workload {}", trigger.id(), workloadId, e);
            }
        }
        workloads.remove(workloadId);
        LOG.info("Workload {} stopped", workloadId);

        return WorkloadStopResponse.newBuilder()
                .setWorkloadStatus(WorkloadStatus.newBuilder()
                        .setWorkloadId(workloadId)
                        .setWorkloadState(WorkloadProto.WorkloadState.WORKLOAD_STATE_STOPPING)
                        .setMessage("workload stopped")
                        .build())
                .build();
    }

    public ManagedWorkload getWorkload(String workloadId) {
        return workloads.get(workloadId);
    }

    public int workloadCount() {
        return workloads.size();
    }

    public void markRunning(String workloadId) {
        var managed = workloads.get(workloadId);
        if (managed != null) {
            managed.setState(WorkloadState.RUNNING);
            managed.setMessage("workload running");
        }
    }

    public void markError(String workloadId, String message) {
        var managed = workloads.get(workloadId);
        if (managed != null) {
            managed.setState(WorkloadState.ERROR);
            managed.setMessage(message);
        }
    }
}

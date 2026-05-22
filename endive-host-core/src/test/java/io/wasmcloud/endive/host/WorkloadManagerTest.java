package io.wasmcloud.endive.host;

import io.wasmcloud.endive.proto.WorkloadProto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkloadManagerTest {

    private static final byte[] BYTES = {1, 2, 3};

    @Test
    void startWorkload_registersWithStartingState() {
        var mgr = new WorkloadManager();
        var resp = mgr.startWorkload("w1", BYTES);
        assertEquals(WorkloadProto.WorkloadState.WORKLOAD_STATE_STARTING,
                resp.getWorkloadStatus().getWorkloadState());
        assertEquals(1, mgr.workloadCount());
    }

    @Test
    void startWorkload_rejectsDuplicateId() {
        var mgr = new WorkloadManager();
        mgr.startWorkload("w1", BYTES);
        var resp = mgr.startWorkload("w1", BYTES);
        assertEquals(WorkloadProto.WorkloadState.WORKLOAD_STATE_ERROR,
                resp.getWorkloadStatus().getWorkloadState());
        assertEquals(1, mgr.workloadCount());
    }

    @Test
    void markRunning_transitionsToRunning() {
        var mgr = new WorkloadManager();
        mgr.startWorkload("w1", BYTES);
        mgr.markRunning("w1");
        var resp = mgr.statusWorkload("w1");
        assertEquals(WorkloadProto.WorkloadState.WORKLOAD_STATE_RUNNING,
                resp.getWorkloadStatus().getWorkloadState());
    }

    @Test
    void markError_recordsMessage() {
        var mgr = new WorkloadManager();
        mgr.startWorkload("w1", BYTES);
        mgr.markError("w1", "boom");
        var resp = mgr.statusWorkload("w1");
        assertEquals(WorkloadProto.WorkloadState.WORKLOAD_STATE_ERROR,
                resp.getWorkloadStatus().getWorkloadState());
        assertEquals("boom", resp.getWorkloadStatus().getMessage());
    }

    @Test
    void statusWorkload_unknownIdReturnsNotFound() {
        var mgr = new WorkloadManager();
        var resp = mgr.statusWorkload("ghost");
        assertEquals(WorkloadProto.WorkloadState.WORKLOAD_STATE_NOT_FOUND,
                resp.getWorkloadStatus().getWorkloadState());
    }

    @Test
    void stopWorkload_removesAndReportsStopping() {
        var mgr = new WorkloadManager();
        mgr.startWorkload("w1", BYTES);
        var resp = mgr.stopWorkload("w1");
        assertEquals(WorkloadProto.WorkloadState.WORKLOAD_STATE_STOPPING,
                resp.getWorkloadStatus().getWorkloadState());
        assertEquals(0, mgr.workloadCount());
    }

    @Test
    void stopWorkload_unknownReturnsNotFound() {
        var mgr = new WorkloadManager();
        var resp = mgr.stopWorkload("ghost");
        assertEquals(WorkloadProto.WorkloadState.WORKLOAD_STATE_NOT_FOUND,
                resp.getWorkloadStatus().getWorkloadState());
    }

    @Test
    void getWorkload_returnsManagedAfterStart() {
        var mgr = new WorkloadManager();
        mgr.startWorkload("w1", BYTES);
        var managed = mgr.getWorkload("w1");
        assertNotNull(managed);
        assertEquals("w1", managed.workloadId());
        assertArrayEquals(BYTES, managed.wasmBytes());
    }
}

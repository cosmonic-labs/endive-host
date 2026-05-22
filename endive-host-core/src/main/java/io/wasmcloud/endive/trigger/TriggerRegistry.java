package io.wasmcloud.endive.trigger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class TriggerRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(TriggerRegistry.class);
    private final ConcurrentHashMap<String, List<TriggerSource>> workloadTriggers = new ConcurrentHashMap<>();

    public void registerTrigger(String workloadId, TriggerSource trigger, TriggerCallback callback) {
        workloadTriggers.computeIfAbsent(workloadId, k -> new CopyOnWriteArrayList<>()).add(trigger);
        trigger.start(callback);
        LOG.info("Registered trigger {} for workload {}", trigger.id(), workloadId);
    }

    public void stopTriggers(String workloadId) {
        var triggers = workloadTriggers.remove(workloadId);
        if (triggers != null) {
            for (var trigger : triggers) {
                try {
                    trigger.stop();
                } catch (Exception e) {
                    LOG.error("Error stopping trigger {}", trigger.id(), e);
                }
            }
        }
    }

    public void stopAll() {
        for (var entry : workloadTriggers.entrySet()) {
            stopTriggers(entry.getKey());
        }
    }
}

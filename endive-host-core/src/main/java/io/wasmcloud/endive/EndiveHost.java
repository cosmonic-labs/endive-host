package io.wasmcloud.endive;

import com.google.protobuf.Timestamp;
import io.wasmcloud.endive.config.HostConfig;
import io.wasmcloud.endive.engine.EndiveWasmEngine;
import io.wasmcloud.endive.engine.OciFetcher;
import io.wasmcloud.endive.engine.WasmEngine;
import io.wasmcloud.endive.engine.WorkloadInvoker;
import io.wasmcloud.endive.host.*;
import io.wasmcloud.endive.http.HttpServer;
import io.wasmcloud.endive.nats.NatsControlPlane;
import io.wasmcloud.endive.proto.HostHeartbeatProto.HostHeartbeat;
import io.wasmcloud.endive.proto.WitInterfaceProto.WitInterface;
import io.wasmcloud.endive.proto.WorkloadProto;
import io.wasmcloud.endive.proto.WorkloadServiceProto.*;
import io.wasmcloud.endive.sysinfo.SystemInfo;
import io.wasmcloud.endive.trigger.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class EndiveHost implements HostApi {
    private static final Logger LOG = LoggerFactory.getLogger(EndiveHost.class);
    private static final String VERSION = "0.1.0";

    private final String hostId;
    private final String friendlyName;
    private final Map<String, String> labels;
    private final Instant startedAt;
    private final SystemInfo systemInfo;
    private final WorkloadManager workloadManager;
    private final TriggerRegistry triggerRegistry;
    private final WasmEngine wasmEngine;
    private final WorkloadInvoker workloadInvoker;
    private final HttpServer httpServer;
    private final OciFetcher ociFetcher;
    private final HostConfig config;
    private volatile NatsControlPlane controlPlane;

    public EndiveHost(HostConfig config) {
        this.config = config;
        this.hostId = config.host().id().isEmpty() ? UUID.randomUUID().toString() : config.host().id();
        this.friendlyName = config.host().friendlyName().isEmpty()
                ? generateFriendlyName()
                : config.host().friendlyName();
        this.labels = config.host().labels();
        this.labels.put("runtime", "endive");
        this.labels.put("hostgroup", config.host().hostGroup());
        this.startedAt = Instant.now();
        this.systemInfo = new SystemInfo();
        this.workloadManager = new WorkloadManager();
        this.triggerRegistry = new TriggerRegistry();
        this.wasmEngine = new EndiveWasmEngine();
        this.workloadInvoker = new WorkloadInvoker(wasmEngine);
        this.httpServer = new HttpServer(config.http().bindAddress(), config.http().port());
        this.ociFetcher = new OciFetcher();
    }

    public void start() throws Exception {
        httpServer.start();

        var natsConn = NatsControlPlane.connectNats(config.nats().url());
        controlPlane = new NatsControlPlane(
                this, hostId, natsConn,
                config.heartbeat().intervalSeconds()
        );
        controlPlane.start();

        // Start pre-configured workloads
        for (var wc : config.workloads()) {
            try {
                startConfiguredWorkload(wc, natsConn);
            } catch (Exception e) {
                LOG.error("Failed to start pre-configured workload {}", wc.id(), e);
            }
        }

        LOG.info("Endive host {} ({}) started", hostId, friendlyName);
    }

    private void startConfiguredWorkload(HostConfig.WorkloadConfig wc, io.nats.client.Connection natsConn) throws IOException {
        byte[] wasmBytes = Files.readAllBytes(Path.of(wc.wasmPath()));
        var startResp = workloadManager.startWorkload(wc.id(), wasmBytes);

        var managed = workloadManager.getWorkload(wc.id());
        if (managed == null) return;

        try {
            var module = wasmEngine.loadModule(wasmBytes);
            managed.setModule(module);

            TriggerCallback callback = event -> workloadInvoker.invoke(wasmBytes, event, wc.env());

            if (wc.trigger() != null) {
                TriggerSource trigger = createTrigger(wc.id(), wc.trigger(), natsConn);
                triggerRegistry.registerTrigger(wc.id(), trigger, callback);
                managed.addTrigger(trigger);
            }

            workloadManager.markRunning(wc.id());
        } catch (Exception e) {
            workloadManager.markError(wc.id(), e.getMessage());
        }
    }

    private TriggerSource createTrigger(String workloadId, HostConfig.TriggerConfig tc, io.nats.client.Connection natsConn) {
        return switch (tc.type()) {
            case "cron" -> new CronTrigger(workloadId + "-cron", tc.schedule());
            case "nats" -> new NatsEventTrigger(workloadId + "-nats", tc.subject(), natsConn);
            case "http" -> new HttpWebhookTrigger(workloadId + "-http", tc.path(), httpServer);
            default -> throw new IllegalArgumentException("Unknown trigger type: " + tc.type());
        };
    }

    public void stop() {
        triggerRegistry.stopAll();
        if (controlPlane != null) {
            controlPlane.stop();
        }
        httpServer.stop();
        LOG.info("Endive host {} stopped", hostId);
    }

    public void awaitTermination() throws InterruptedException {
        Thread.currentThread().join();
    }

    public String hostId() {
        return hostId;
    }

    @Override
    public HostHeartbeat heartbeat() {
        return HostHeartbeat.newBuilder()
                .setId(hostId)
                .setHostname(systemInfo.hostname())
                .setFriendlyName(friendlyName)
                .setVersion(VERSION)
                .putAllLabels(labels)
                .setStartedAt(Timestamp.newBuilder()
                        .setSeconds(startedAt.getEpochSecond())
                        .setNanos(startedAt.getNano())
                        .build())
                .setOsArch(systemInfo.osArch())
                .setOsName(systemInfo.osName())
                .setOsKernel(systemInfo.osVersion())
                .setSystemCpuUsage(systemInfo.systemCpuUsage())
                .setSystemMemoryTotal(systemInfo.systemMemoryTotal())
                .setSystemMemoryFree(systemInfo.systemMemoryFree())
                .setWorkloadCount(workloadManager.workloadCount())
                .setHttpPort(config.http().port())
                .build();
    }

    @Override
    public WorkloadStartResponse workloadStart(WorkloadStartRequest request) {
        String workloadId = request.getWorkloadId();
        if (workloadId.isEmpty()) {
            return WorkloadStartResponse.newBuilder()
                    .setWorkloadStatus(WorkloadStatus.newBuilder()
                            .setWorkloadId("")
                            .setWorkloadState(WorkloadProto.WorkloadState.WORKLOAD_STATE_ERROR)
                            .setMessage("workload_id is required")
                            .build())
                    .build();
        }

        var workload = request.getWorkload();
        if (workload == null) {
            return errorResponse(workloadId, "workload is required");
        }

        // The operator places the wasm image in either Service.image (long-running
        // wasi:cli/run-style service) or WitWorld.components[0].image (the
        // common "component(s) + host_interfaces" deployment). Resolve once.
        String image;
        Map<String, String> env;
        if (workload.hasService() && !workload.getService().getImage().isEmpty()) {
            image = workload.getService().getImage();
            env = workload.getService().hasLocalResources()
                    ? workload.getService().getLocalResources().getEnvironmentMap()
                    : Map.of();
        } else if (workload.hasWitWorld() && workload.getWitWorld().getComponentsCount() > 0
                && !workload.getWitWorld().getComponents(0).getImage().isEmpty()) {
            var comp = workload.getWitWorld().getComponents(0);
            image = comp.getImage();
            env = comp.hasLocalResources() ? comp.getLocalResources().getEnvironmentMap() : Map.of();
            if (workload.getWitWorld().getComponentsCount() > 1) {
                LOG.warn("Workload {} has {} components; only the first ({}) is invoked by this host",
                        workloadId, workload.getWitWorld().getComponentsCount(), comp.getName());
            }
        } else {
            return errorResponse(workloadId, "workload requires service.image or wit_world.components[0].image");
        }

        try {
            byte[] wasmBytes = ociFetcher.fetch(image);
            var resp = workloadManager.startWorkload(workloadId, wasmBytes);

            var managed = workloadManager.getWorkload(workloadId);
            if (managed != null) {
                var module = wasmEngine.loadModule(wasmBytes);
                managed.setModule(module);

                bindHostInterfaceTriggers(workloadId, workload, wasmBytes, env);

                workloadManager.markRunning(workloadId);
            }

            return workloadManager.statusWorkload(workloadId)
                    .getWorkloadStatus() != null
                    ? WorkloadStartResponse.newBuilder()
                    .setWorkloadStatus(workloadManager.statusWorkload(workloadId).getWorkloadStatus())
                    .build()
                    : resp;
        } catch (Exception e) {
            LOG.error("Failed to start workload {}", workloadId, e);
            return WorkloadStartResponse.newBuilder()
                    .setWorkloadStatus(WorkloadStatus.newBuilder()
                            .setWorkloadId(workloadId)
                            .setWorkloadState(WorkloadProto.WorkloadState.WORKLOAD_STATE_ERROR)
                            .setMessage(e.getMessage())
                            .build())
                    .build();
        }
    }

    /**
     * Inspect the workload's WIT world for interfaces this host can serve and
     * register triggers for each. Today only {@code wasi:http/incoming-handler}
     * is wired, bound as a JSON-over-stdio HTTP route on the host's Undertow
     * server (the route path comes from interface config {@code path}; default
     * {@code /<workloadId>}).
     */
    private void bindHostInterfaceTriggers(String workloadId, WorkloadProto.Workload workload,
                                           byte[] wasmBytes, Map<String, String> env) {
        if (!workload.hasWitWorld()) return;
        for (WitInterface iface : workload.getWitWorld().getHostInterfacesList()) {
            if (isWasiHttpIncomingHandler(iface)) {
                String path = iface.getConfigOrDefault("path", "/" + workloadId);
                TriggerSource trigger = new HttpWebhookTrigger(workloadId + "-http", path, httpServer);
                TriggerCallback cb = event -> workloadInvoker.invoke(wasmBytes, event, env);
                triggerRegistry.registerTrigger(workloadId, trigger, cb);
                var managed = workloadManager.getWorkload(workloadId);
                if (managed != null) managed.addTrigger(trigger);
                LOG.info("Workload {} bound wasi:http/incoming-handler at {}", workloadId, path);
            }
        }
    }

    private static WorkloadStartResponse errorResponse(String workloadId, String message) {
        return WorkloadStartResponse.newBuilder()
                .setWorkloadStatus(WorkloadStatus.newBuilder()
                        .setWorkloadId(workloadId)
                        .setWorkloadState(WorkloadProto.WorkloadState.WORKLOAD_STATE_ERROR)
                        .setMessage(message)
                        .build())
                .build();
    }

    private static boolean isWasiHttpIncomingHandler(WitInterface iface) {
        return "wasi".equals(iface.getNamespace())
                && "http".equals(iface.getPackage())
                && iface.getInterfacesList().contains("incoming-handler");
    }

    @Override
    public WorkloadStatusResponse workloadStatus(WorkloadStatusRequest request) {
        return workloadManager.statusWorkload(request.getWorkloadId());
    }

    @Override
    public WorkloadStopResponse workloadStop(WorkloadStopRequest request) {
        triggerRegistry.stopTriggers(request.getWorkloadId());
        return workloadManager.stopWorkload(request.getWorkloadId());
    }

    private static String generateFriendlyName() {
        var adjectives = new String[]{"autumn", "bitter", "blue", "brave", "bright", "calm", "cold", "cool",
                "crimson", "dark", "dawn", "dry", "early", "empty", "fading", "falling", "flat", "fragrant",
                "gentle", "green", "hidden", "holy", "icy", "late", "lingering", "little", "lively", "long",
                "misty", "morning", "muddy", "nameless", "old", "patient", "polished", "proud", "purple",
                "quiet", "red", "restless", "rough", "shy", "silent", "small", "snowy", "solitary", "spring",
                "still", "summer", "throbbing", "twilight", "wandering", "weathered", "white", "wild",
                "winter", "wispy", "withered", "young"};
        var nouns = new String[]{"bird", "breeze", "brook", "bush", "butterfly", "cherry", "cloud", "darkness",
                "dawn", "dew", "dream", "dust", "feather", "field", "fire", "firefly", "flower", "fog",
                "forest", "frog", "frost", "glade", "glitter", "grass", "haze", "hill", "lake", "leaf",
                "meadow", "moon", "morning", "mountain", "night", "paper", "pine", "pond", "rain", "resonance",
                "river", "sea", "shadow", "shape", "silence", "sky", "smoke", "snow", "snowflake", "sound",
                "star", "sun", "sunset", "surf", "thunder", "tree", "violet", "voice", "water", "waterfall",
                "wave", "wildflower", "wind", "wood"};
        var rng = new java.util.Random();
        return adjectives[rng.nextInt(adjectives.length)] + "-" +
                nouns[rng.nextInt(nouns.length)] + "-" +
                (1000 + rng.nextInt(9000));
    }
}

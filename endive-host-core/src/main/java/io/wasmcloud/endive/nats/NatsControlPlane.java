package io.wasmcloud.endive.nats;

import com.google.protobuf.util.JsonFormat;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.wasmcloud.endive.host.HostApi;
import io.wasmcloud.endive.proto.HostHeartbeatProto.HostHeartbeat;
import io.wasmcloud.endive.proto.WorkloadServiceProto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NatsControlPlane {
    private static final Logger LOG = LoggerFactory.getLogger(NatsControlPlane.class);

    public static final String HOST_API_PREFIX = "runtime.host";
    public static final String OPERATOR_API_PREFIX = "runtime.operator";
    private static final long DEFAULT_HEARTBEAT_INTERVAL_SECONDS = 15;

    private final HostApi hostApi;
    private final String hostId;
    private final Connection natsConnection;
    private final long heartbeatIntervalSeconds;
    private final ScheduledExecutorService heartbeatExecutor;
    private final JsonFormat.Printer jsonPrinter;
    private volatile Dispatcher apiDispatcher;

    public NatsControlPlane(HostApi hostApi, String hostId, Connection natsConnection) {
        this(hostApi, hostId, natsConnection, DEFAULT_HEARTBEAT_INTERVAL_SECONDS);
    }

    public NatsControlPlane(HostApi hostApi, String hostId, Connection natsConnection, long heartbeatIntervalSeconds) {
        this.hostApi = hostApi;
        this.hostId = hostId;
        this.natsConnection = natsConnection;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "heartbeat-publisher");
            t.setDaemon(true);
            return t;
        });
        this.jsonPrinter = JsonFormat.printer()
                .includingDefaultValueFields()
                .preservingProtoFieldNames();
    }

    public void start() {
        String hostSubject = hostSubject(hostId);
        String heartbeatSubject = heartbeatSubject(hostId);

        apiDispatcher = natsConnection.createDispatcher(msg -> {
            try {
                String command = extractCommand(msg.getSubject());
                byte[] response = handleCommand(command, msg.getData());
                if (msg.getReplyTo() != null) {
                    natsConnection.publish(msg.getReplyTo(), response);
                }
            } catch (Exception e) {
                LOG.error("Error handling API command on subject {}", msg.getSubject(), e);
            }
        });
        apiDispatcher.subscribe(hostSubject);
        LOG.info("Subscribed to API subject: {}", hostSubject);

        heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                HostHeartbeat heartbeat = hostApi.heartbeat();
                String json = jsonPrinter.print(heartbeat);
                natsConnection.publish(heartbeatSubject, json.getBytes(StandardCharsets.UTF_8));
                LOG.debug("Published heartbeat to {}", heartbeatSubject);
            } catch (Exception e) {
                LOG.error("Error publishing heartbeat", e);
            }
        }, 0, heartbeatIntervalSeconds, TimeUnit.SECONDS);

        LOG.info("NATS control plane started for host {}", hostId);
    }

    public void stop() {
        if (apiDispatcher != null) {
            natsConnection.closeDispatcher(apiDispatcher);
        }
        heartbeatExecutor.shutdown();
        LOG.info("NATS control plane stopped for host {}", hostId);
    }

    private String extractCommand(String subject) {
        // Subject format: runtime.host.<host-id>.command.parts
        String[] parts = subject.split("\\.");
        if (parts.length <= 3) {
            return "";
        }
        var sb = new StringBuilder();
        for (int i = 3; i < parts.length; i++) {
            if (i > 3) sb.append('.');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private byte[] handleCommand(String command, byte[] payload) throws Exception {
        return switch (command) {
            case "heartbeat" -> {
                HostHeartbeat hb = hostApi.heartbeat();
                yield jsonPrinter.print(hb).getBytes(StandardCharsets.UTF_8);
            }
            case "workload.start" -> {
                var builder = WorkloadStartRequest.newBuilder();
                JsonFormat.parser().ignoringUnknownFields().merge(new String(payload, StandardCharsets.UTF_8), builder);
                var resp = hostApi.workloadStart(builder.build());
                yield jsonPrinter.print(resp).getBytes(StandardCharsets.UTF_8);
            }
            case "workload.stop" -> {
                var builder = WorkloadStopRequest.newBuilder();
                JsonFormat.parser().ignoringUnknownFields().merge(new String(payload, StandardCharsets.UTF_8), builder);
                var resp = hostApi.workloadStop(builder.build());
                yield jsonPrinter.print(resp).getBytes(StandardCharsets.UTF_8);
            }
            case "workload.status" -> {
                var builder = WorkloadStatusRequest.newBuilder();
                JsonFormat.parser().ignoringUnknownFields().merge(new String(payload, StandardCharsets.UTF_8), builder);
                var resp = hostApi.workloadStatus(builder.build());
                yield jsonPrinter.print(resp).getBytes(StandardCharsets.UTF_8);
            }
            default -> {
                LOG.warn("Unknown command: {}", command);
                yield "{}".getBytes(StandardCharsets.UTF_8);
            }
        };
    }

    public static String hostSubject(String hostId) {
        return HOST_API_PREFIX + "." + hostId + ".>";
    }

    public static String heartbeatSubject(String hostId) {
        return OPERATOR_API_PREFIX + ".heartbeat." + hostId;
    }

    public static String rpcSubject(String hostId, String command) {
        return HOST_API_PREFIX + "." + hostId + "." + command;
    }

    public static Connection connectNats(String url) throws Exception {
        var options = new Options.Builder()
                .server(url)
                .build();
        return Nats.connect(options);
    }

    public Connection connection() {
        return natsConnection;
    }
}

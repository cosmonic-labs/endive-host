package io.wasmcloud.endive.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class HostConfig {
    @JsonProperty("host")
    private HostSection host = new HostSection();

    @JsonProperty("nats")
    private NatsSection nats = new NatsSection();

    @JsonProperty("http")
    private HttpSection http = new HttpSection();

    @JsonProperty("heartbeat")
    private HeartbeatSection heartbeat = new HeartbeatSection();

    @JsonProperty("workloads")
    private List<WorkloadConfig> workloads = new ArrayList<>();

    public HostSection host() { return host; }
    public NatsSection nats() { return nats; }
    public HttpSection http() { return http; }
    public HeartbeatSection heartbeat() { return heartbeat; }
    public List<WorkloadConfig> workloads() { return workloads; }

    public static HostConfig load(String path) throws IOException {
        var mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(new File(path), HostConfig.class);
    }

    public static HostConfig defaults() {
        return new HostConfig();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HostSection {
        @JsonProperty("id")
        private String id = "";
        @JsonProperty("friendly-name")
        private String friendlyName = "";
        @JsonProperty("host-group")
        private String hostGroup = "default";
        @JsonProperty("labels")
        private Map<String, String> labels = new LinkedHashMap<>();

        public String id() { return id; }
        public String friendlyName() { return friendlyName; }
        public String hostGroup() { return hostGroup; }
        public Map<String, String> labels() { return labels; }
        public void setId(String id) { this.id = id; }
        public void setFriendlyName(String friendlyName) { this.friendlyName = friendlyName; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NatsSection {
        @JsonProperty("url")
        private String url = "nats://localhost:4222";
        @JsonProperty("tls")
        private TlsSection tls = new TlsSection();

        public String url() { return url; }
        public TlsSection tls() { return tls; }
        public void setUrl(String url) { this.url = url; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TlsSection {
        @JsonProperty("ca-cert")
        private String caCert = "";
        @JsonProperty("client-cert")
        private String clientCert = "";
        @JsonProperty("client-key")
        private String clientKey = "";

        public String caCert() { return caCert; }
        public String clientCert() { return clientCert; }
        public String clientKey() { return clientKey; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HttpSection {
        @JsonProperty("port")
        private int port = 8080;
        @JsonProperty("bind-address")
        private String bindAddress = "0.0.0.0";

        public int port() { return port; }
        public String bindAddress() { return bindAddress; }
        public void setPort(int port) { this.port = port; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HeartbeatSection {
        @JsonProperty("interval-seconds")
        private int intervalSeconds = 15;

        public int intervalSeconds() { return intervalSeconds; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkloadConfig {
        @JsonProperty("id")
        private String id;
        @JsonProperty("wasm-path")
        private String wasmPath;
        @JsonProperty("trigger")
        private TriggerConfig trigger;
        @JsonProperty("env")
        private Map<String, String> env = new LinkedHashMap<>();

        public String id() { return id; }
        public String wasmPath() { return wasmPath; }
        public TriggerConfig trigger() { return trigger; }
        public Map<String, String> env() { return env; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TriggerConfig {
        @JsonProperty("type")
        private String type;
        @JsonProperty("schedule")
        private String schedule;
        @JsonProperty("path")
        private String path;
        @JsonProperty("subject")
        private String subject;

        public String type() { return type; }
        public String schedule() { return schedule; }
        public String path() { return path; }
        public String subject() { return subject; }
    }
}

package io.wasmcloud.endive;

import io.wasmcloud.endive.config.HostConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.util.concurrent.Callable;

@Command(name = "endive-host", mixinStandardHelpOptions = true,
        version = "endive-host 0.1.0",
        description = "JVM-based WebAssembly host for wasmCloud using Endive runtime")
public class Main implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    @Spec
    private CommandSpec spec;

    @Option(names = {"-c", "--config"}, description = "Path to endive-host.yaml config file")
    private String configPath;

    @Option(names = "--nats-url", description = "NATS server URL (default: ${DEFAULT-VALUE})",
            defaultValue = "nats://localhost:4222")
    private String natsUrl;

    @Option(names = "--http-port", description = "HTTP server port (default: ${DEFAULT-VALUE})",
            defaultValue = "8080")
    private int httpPort;

    @Option(names = "--host-group", description = "Host group name (default: ${DEFAULT-VALUE})",
            defaultValue = "default")
    private String hostGroup;

    @Option(names = "--host-id", description = "Host ID (default: auto-generated UUID)")
    private String hostId;

    @Option(names = "--host-name", description = "Friendly host name (default: auto-generated)")
    private String hostName;

    @Override
    public Integer call() throws Exception {
        var config = configPath != null ? HostConfig.load(configPath) : HostConfig.defaults();

        // CLI flags override config file values when explicitly provided
        var parseResult = spec.commandLine().getParseResult();
        if (configPath == null || parseResult.hasMatchedOption("--nats-url")) {
            config.nats().setUrl(natsUrl);
        }
        if (configPath == null || parseResult.hasMatchedOption("--http-port")) {
            config.http().setPort(httpPort);
        }
        if (configPath == null || parseResult.hasMatchedOption("--host-group")) {
            config.host().labels().put("hostgroup", hostGroup);
        }
        if (parseResult.hasMatchedOption("--host-id")) {
            config.host().setId(hostId);
        }
        if (parseResult.hasMatchedOption("--host-name")) {
            config.host().setFriendlyName(hostName);
        }

        var host = new EndiveHost(config);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutdown signal received");
            host.stop();
        }));

        host.start();
        LOG.info("Host {} is running. Press Ctrl+C to stop.", host.hostId());
        host.awaitTermination();
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}

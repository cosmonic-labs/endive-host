package io.wasmcloud.endive;

import io.wasmcloud.endive.config.HostConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "endive-host", mixinStandardHelpOptions = true,
        version = "endive-host 0.1.0",
        description = "JVM-based WebAssembly host for wasmCloud using Endive runtime")
public class Main implements Callable<Integer> {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

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

        // picocli fields already reflect CLI > YAML > annotation defaults
        config.nats().setUrl(natsUrl);
        config.http().setPort(httpPort);
        config.host().labels().put("hostgroup", hostGroup);
        if (hostId != null) config.host().setId(hostId);
        if (hostName != null) config.host().setFriendlyName(hostName);

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
        var cmd = new CommandLine(new Main());
        // Two-pass: first parse to find --config, then set defaults from YAML
        try {
            var parseResult = cmd.parseArgs(args);
            var main = (Main) parseResult.commandSpec().userObject();
            if (main.configPath != null) {
                cmd.setDefaultValueProvider(new HostConfigDefaultProvider(main.configPath));
            }
        } catch (CommandLine.ParameterException ignored) {
            // Let execute() handle the error with proper error messaging
        } catch (Exception e) {
            LOG.error("Failed to load config", e);
            System.exit(1);
        }
        int exitCode = cmd.execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}

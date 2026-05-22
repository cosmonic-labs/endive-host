package io.wasmcloud.endive;

import io.wasmcloud.endive.config.HostConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            var config = parseArgs(args);
            var host = new EndiveHost(config);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOG.info("Shutdown signal received");
                host.stop();
            }));

            host.start();
            LOG.info("Host {} is running. Press Ctrl+C to stop.", host.hostId());
            host.awaitTermination();
        } catch (Exception e) {
            LOG.error("Fatal error", e);
            System.exit(1);
        }
    }

    private static HostConfig parseArgs(String[] args) throws Exception {
        var config = HostConfig.defaults();
        String configPath = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--config", "-c" -> configPath = args[++i];
                case "--nats-url" -> config.nats().setUrl(args[++i]);
                case "--http-port" -> config.http().setPort(Integer.parseInt(args[++i]));
                case "--host-group" -> {
                    // Set via labels
                    config.host().labels().put("hostgroup", args[++i]);
                }
                case "--host-id" -> config.host().setId(args[++i]);
                case "--host-name" -> config.host().setFriendlyName(args[++i]);
                case "--help", "-h" -> {
                    printUsage();
                    System.exit(0);
                }
                default -> {
                    System.err.println("Unknown argument: " + args[i]);
                    printUsage();
                    System.exit(1);
                }
            }
        }

        if (configPath != null) {
            config = HostConfig.load(configPath);
            // Re-apply CLI overrides
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--config", "-c" -> i++;
                    case "--nats-url" -> config.nats().setUrl(args[++i]);
                    case "--http-port" -> config.http().setPort(Integer.parseInt(args[++i]));
                    case "--host-group" -> config.host().labels().put("hostgroup", args[++i]);
                    case "--host-id" -> config.host().setId(args[++i]);
                    case "--host-name" -> config.host().setFriendlyName(args[++i]);
                }
            }
        }

        return config;
    }

    private static void printUsage() {
        System.out.println("""
                Usage: java -jar endive-host.jar [options]

                Options:
                  --config, -c <path>     Path to endive-host.yaml config file
                  --nats-url <url>        NATS server URL (default: nats://localhost:4222)
                  --http-port <port>      HTTP server port (default: 8080)
                  --host-group <group>    Host group name (default: default)
                  --host-id <id>          Host ID (default: auto-generated UUID)
                  --host-name <name>      Friendly host name (default: auto-generated)
                  --help, -h              Show this help message
                """);
    }
}

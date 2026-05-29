package io.wasmcloud.endive;

import io.wasmcloud.endive.config.HostConfig;
import picocli.CommandLine;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.OptionSpec;

import java.io.IOException;

class HostConfigDefaultProvider implements CommandLine.IDefaultValueProvider {
    private final HostConfig config;

    HostConfigDefaultProvider(String configPath) throws IOException {
        this.config = HostConfig.load(configPath);
    }

    @Override
    public String defaultValue(ArgSpec argSpec) {
        if (!(argSpec instanceof OptionSpec option)) {
            return null;
        }
        return switch (option.longestName()) {
            case "--nats-url" -> config.nats().url();
            case "--http-port" -> String.valueOf(config.http().port());
            case "--host-group" -> config.host().hostGroup();
            case "--host-id" -> emptyToNull(config.host().id());
            case "--host-name" -> emptyToNull(config.host().friendlyName());
            default -> null;
        };
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    HostConfig config() {
        return config;
    }
}

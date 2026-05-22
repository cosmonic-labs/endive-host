package io.wasmcloud.endive.examples.vertx;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class App {
    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8088"));
        Vertx.vertx().deployVerticle(new ApiVerticle(port))
                .onSuccess(id -> LOG.info("Deployed ApiVerticle (deployment id {}) on :{}", id, port))
                .onFailure(t -> {
                    LOG.error("Failed to deploy ApiVerticle", t);
                    System.exit(1);
                });
    }
}

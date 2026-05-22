package io.wasmcloud.endive.examples.vertx;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

import java.time.Instant;

public class TimeFunction implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext rc) {
        rc.response()
                .putHeader("content-type", "text/plain")
                .end(Instant.now().toString() + "\n");
    }
}

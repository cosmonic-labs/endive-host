package io.wasmcloud.endive.examples.vertx;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

public class HelloFunction implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext rc) {
        rc.response()
                .putHeader("content-type", "text/plain")
                .end("hello from java\n");
    }
}

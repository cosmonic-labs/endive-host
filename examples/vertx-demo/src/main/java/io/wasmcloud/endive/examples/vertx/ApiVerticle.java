package io.wasmcloud.endive.examples.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Composes several "serverless functions" — plain Vert.x {@code Handler<RoutingContext>}
 * implementations — onto a single shared HTTP server. Two are pure Java; one delegates
 * to a WASI Preview 1 module via the embedded endive-host engine.
 */
public class ApiVerticle extends AbstractVerticle {
    private static final Logger LOG = LoggerFactory.getLogger(ApiVerticle.class);

    private final int port;

    public ApiVerticle(int port) {
        this.port = port;
    }

    @Override
    public void start(Promise<Void> startPromise) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        router.get("/hello").handler(new HelloFunction());
        router.get("/time").handler(new TimeFunction());

        var wasmFn = WasmFunction.fromClasspath("/hello.wasm");
        router.get("/wasm/hello").handler(wasmFn);
        router.post("/wasm/hello").handler(wasmFn);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onSuccess(s -> {
                    LOG.info("HTTP server listening on :{}", port);
                    LOG.info("Routes: GET /hello (java)  GET /time (java)  GET|POST /wasm/hello (endive)");
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }
}

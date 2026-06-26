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

        var helloWasm = WasmFunction.fromClasspath("/hello.wasm", "text/plain; charset=utf-8");
        router.get("/wasm/hello").handler(helloWasm);
        router.post("/wasm/hello").handler(helloWasm);

        var markdownInvoker = WasmInvoker.fromClasspath("/markdown.wasm");
        router.post("/wasm/markdown").handler(
                new WasmFunction(markdownInvoker, "text/html; charset=utf-8"));

        // Java handler that calls the markdown wasm in-process (no HTTP hop).
        router.post("/render").handler(new RenderFunction(markdownInvoker));

        // Same composition, but Avro binary on the wire in and out.
        router.post("/render/avro").handler(new AvroRenderFunction(markdownInvoker));

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(port)
                .onSuccess(s -> {
                    LOG.info("HTTP server listening on :{}", port);
                    LOG.info("Routes:");
                    LOG.info("  GET       /hello          (java)");
                    LOG.info("  GET       /time           (java)");
                    LOG.info("  GET|POST  /wasm/hello     (endive: hello.wasm)");
                    LOG.info("  POST      /wasm/markdown  (endive: markdown.wasm)");
                    LOG.info("  POST      /render         (java -> wasm markdown -> java)");
                    LOG.info("  POST      /render/avro    (avro -> wasm markdown -> avro)");
                    startPromise.complete();
                })
                .onFailure(startPromise::fail);
    }
}

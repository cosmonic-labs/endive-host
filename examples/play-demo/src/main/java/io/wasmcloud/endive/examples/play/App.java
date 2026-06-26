package io.wasmcloud.endive.examples.play;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.routing.RoutingDsl;
import play.server.Server;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Embeds Play Framework's HTTP server (via {@link Server#forRouter}) with no
 * sbt and no {@code conf/routes} file — routes are declared in Java with the
 * {@link RoutingDsl}. Composes several "serverless functions": two pure Java,
 * three backed by WASI Preview 1 modules running on the embedded endive-host
 * engine, all in one JVM sharing one HTTP server.
 */
public final class App {
    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8088"));

        // Wasm invocation is blocking; run it off Play's request threads.
        Executor wasmExecutor = Executors.newCachedThreadPool(daemonFactory("wasm-worker-"));

        HelloFunction hello = new HelloFunction();
        TimeFunction time = new TimeFunction();

        WasmFunction helloWasm = new WasmFunction(
                WasmInvoker.fromClasspath("/hello.wasm", wasmExecutor), "text/plain; charset=utf-8");

        WasmInvoker markdownInvoker = WasmInvoker.fromClasspath("/markdown.wasm", wasmExecutor);
        WasmFunction markdownWasm = new WasmFunction(markdownInvoker, "text/html; charset=utf-8");

        // Java handler that calls the markdown wasm in-process (no HTTP hop).
        RenderFunction render = new RenderFunction(markdownInvoker);

        // Same composition, but Avro binary on the wire in and out.
        AvroRenderFunction avroRender = new AvroRenderFunction(markdownInvoker);

        Server server = Server.forRouter(port, components ->
                RoutingDsl.fromComponents(components)
                        .GET("/hello").routingTo(req -> hello.handle(req))
                        .GET("/time").routingTo(req -> time.handle(req))
                        .GET("/wasm/hello").routingAsync(req -> helloWasm.handle(req))
                        .POST("/wasm/hello").routingAsync(req -> helloWasm.handle(req))
                        .POST("/wasm/markdown").routingAsync(req -> markdownWasm.handle(req))
                        .POST("/render").routingAsync(req -> render.handle(req))
                        .POST("/render/avro").routingAsync(req -> avroRender.handle(req))
                        .build());

        LOG.info("HTTP server listening on :{}", port);
        LOG.info("Routes:");
        LOG.info("  GET       /hello          (java)");
        LOG.info("  GET       /time           (java)");
        LOG.info("  GET|POST  /wasm/hello     (endive: hello.wasm)");
        LOG.info("  POST      /wasm/markdown  (endive: markdown.wasm)");
        LOG.info("  POST      /render         (java -> wasm markdown -> java)");
        LOG.info("  POST      /render/avro    (avro -> wasm markdown -> avro)");

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}

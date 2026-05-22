package io.wasmcloud.endive.examples.vertx;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import io.wasmcloud.endive.engine.EndiveWasmEngine;
import io.wasmcloud.endive.engine.WasmEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * A Vert.x route handler that runs a WASI Preview 1 module via the embedded
 * endive-host engine. The HTTP request body is fed to the module's stdin;
 * the module's stdout becomes the HTTP response body. Invocation runs on a
 * worker thread so the event loop stays unblocked.
 */
public class WasmFunction implements Handler<RoutingContext> {
    private static final Logger LOG = LoggerFactory.getLogger(WasmFunction.class);

    private final byte[] wasmBytes;
    private final WasmEngine engine = new EndiveWasmEngine();

    public WasmFunction(byte[] wasmBytes) {
        this.wasmBytes = wasmBytes;
    }

    /** Load a module from the classpath; useful for shipping demo modules in the fat jar. */
    public static WasmFunction fromClasspath(String resource) {
        try (InputStream in = WasmFunction.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalArgumentException("classpath resource not found: " + resource);
            return new WasmFunction(in.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException("failed to load wasm from classpath " + resource, e);
        }
    }

    @Override
    public void handle(RoutingContext rc) {
        byte[] body = rc.body() != null && rc.body().buffer() != null
                ? rc.body().buffer().getBytes()
                : new byte[0];

        Map<String, String> env = Map.of(
                "REQUEST_METHOD", rc.request().method().name(),
                "REQUEST_PATH", rc.request().path());

        rc.vertx().<byte[]>executeBlocking(() -> {
            var module = engine.loadModule(wasmBytes);
            return module.invoke(body, env);
        }).onSuccess(out -> rc.response()
                        .putHeader("content-type", "application/octet-stream")
                        .end(Buffer.buffer(out)))
                .onFailure(t -> {
                    LOG.error("wasm invocation failed", t);
                    rc.fail(t);
                });
    }
}

package io.wasmcloud.endive.examples.vertx;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * A Vert.x route handler that pipes an HTTP request through a wasm module:
 * request body becomes the module's stdin, the module's stdout becomes the
 * response body.
 */
public class WasmFunction implements Handler<RoutingContext> {
    private static final Logger LOG = LoggerFactory.getLogger(WasmFunction.class);

    private final WasmInvoker invoker;
    private final String responseContentType;

    public WasmFunction(WasmInvoker invoker, String responseContentType) {
        this.invoker = invoker;
        this.responseContentType = responseContentType;
    }

    public static WasmFunction fromClasspath(String resource, String responseContentType) {
        return new WasmFunction(WasmInvoker.fromClasspath(resource), responseContentType);
    }

    @Override
    public void handle(RoutingContext rc) {
        byte[] body = rc.body() != null && rc.body().buffer() != null
                ? rc.body().buffer().getBytes()
                : new byte[0];

        Map<String, String> env = Map.of(
                "REQUEST_METHOD", rc.request().method().name(),
                "REQUEST_PATH", rc.request().path());

        invoker.invoke(rc.vertx(), body, env)
                .onSuccess(out -> rc.response()
                        .putHeader("content-type", responseContentType)
                        .end(Buffer.buffer(out)))
                .onFailure(t -> {
                    LOG.error("wasm invocation failed", t);
                    rc.fail(t);
                });
    }
}

package io.wasmcloud.endive.examples.play;

import org.apache.pekko.util.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * A Play route handler that pipes an HTTP request through a wasm module:
 * request body becomes the module's stdin, the module's stdout becomes the
 * response body.
 */
public class WasmFunction {
    private static final Logger LOG = LoggerFactory.getLogger(WasmFunction.class);

    private final WasmInvoker invoker;
    private final String responseContentType;

    public WasmFunction(WasmInvoker invoker, String responseContentType) {
        this.invoker = invoker;
        this.responseContentType = responseContentType;
    }

    public CompletionStage<Result> handle(Http.Request request) {
        Map<String, String> env = Map.of(
                "REQUEST_METHOD", request.method(),
                "REQUEST_PATH", request.path());

        return invoker.invoke(bodyBytes(request), env)
                .thenApply(out -> Results.ok(out).as(responseContentType))
                .exceptionally(t -> {
                    LOG.error("wasm invocation failed", t);
                    return Results.internalServerError("wasm invocation failed: " + t.getMessage());
                });
    }

    /** Extract the raw request body as bytes regardless of how Play parsed it. */
    static byte[] bodyBytes(Http.Request request) {
        Http.RequestBody body = request.body();
        if (body == null) return new byte[0];
        ByteString bytes = body.asBytes();
        if (bytes != null) return bytes.toArray();
        String text = body.asText();
        if (text != null) return text.getBytes(StandardCharsets.UTF_8);
        return new byte[0];
    }
}

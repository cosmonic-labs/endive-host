package io.wasmcloud.endive.examples.vertx;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.wasmcloud.endive.engine.EndiveWasmEngine;
import io.wasmcloud.endive.engine.WasmEngine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Thin reusable wrapper around the embedded endive {@link WasmEngine}. Callers
 * hand off a stdin byte buffer plus an env map and get back a
 * {@link Future Future&lt;byte[]&gt;} of the module's stdout — invocation
 * runs on a worker so the Vert.x event loop stays unblocked.
 *
 * <p>Used by both {@link WasmFunction} (HTTP bridge to a wasm module) and
 * {@link RenderFunction} (a Java handler that composes Java logic with an
 * in-process wasm call).
 */
public class WasmInvoker {
    private final byte[] wasmBytes;
    private final WasmEngine engine = new EndiveWasmEngine();

    public WasmInvoker(byte[] wasmBytes) {
        this.wasmBytes = wasmBytes;
    }

    public static WasmInvoker fromClasspath(String resource) {
        try (InputStream in = WasmInvoker.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalArgumentException("classpath resource not found: " + resource);
            return new WasmInvoker(in.readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException("failed to load wasm from classpath " + resource, e);
        }
    }

    public Future<byte[]> invoke(Vertx vertx, byte[] stdin, Map<String, String> env) {
        return vertx.executeBlocking(() -> {
            var module = engine.loadModule(wasmBytes);
            return module.invoke(stdin, env);
        });
    }
}

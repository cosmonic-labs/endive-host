package io.wasmcloud.endive.examples.play;

import io.wasmcloud.endive.engine.EndiveWasmEngine;
import io.wasmcloud.endive.engine.WasmEngine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * Thin reusable wrapper around the embedded endive {@link WasmEngine}. Callers
 * hand off a stdin byte buffer plus an env map and get back a
 * {@link CompletionStage CompletionStage&lt;byte[]&gt;} of the module's stdout —
 * invocation runs on a worker {@link Executor} so Play's request threads stay
 * unblocked.
 *
 * <p>Used by both {@link WasmFunction} (HTTP bridge to a wasm module) and
 * {@link RenderFunction} (a Java handler that composes Java logic with an
 * in-process wasm call).
 */
public class WasmInvoker {
    private final byte[] wasmBytes;
    private final WasmEngine engine = new EndiveWasmEngine();
    private final Executor executor;

    public WasmInvoker(byte[] wasmBytes, Executor executor) {
        this.wasmBytes = wasmBytes;
        this.executor = executor;
    }

    public static WasmInvoker fromClasspath(String resource, Executor executor) {
        try (InputStream in = WasmInvoker.class.getResourceAsStream(resource)) {
            if (in == null) throw new IllegalArgumentException("classpath resource not found: " + resource);
            return new WasmInvoker(in.readAllBytes(), executor);
        } catch (IOException e) {
            throw new RuntimeException("failed to load wasm from classpath " + resource, e);
        }
    }

    public CompletionStage<byte[]> invoke(byte[] stdin, Map<String, String> env) {
        return CompletableFuture.supplyAsync(() -> {
            var module = engine.loadModule(wasmBytes);
            return module.invoke(stdin, env);
        }, executor);
    }
}

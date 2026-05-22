package io.wasmcloud.endive.engine;

public interface WasmEngine {
    WasmModule loadModule(byte[] wasmBytes);
}

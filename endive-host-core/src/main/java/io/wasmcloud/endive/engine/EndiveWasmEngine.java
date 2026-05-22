package io.wasmcloud.endive.engine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EndiveWasmEngine implements WasmEngine {
    private static final Logger LOG = LoggerFactory.getLogger(EndiveWasmEngine.class);

    @Override
    public WasmModule loadModule(byte[] wasmBytes) {
        LOG.debug("Loading WASM module ({} bytes)", wasmBytes.length);
        return new EndiveWasmModule(wasmBytes);
    }
}

package io.wasmcloud.endive.engine;

import run.endive.compiler.MachineFactoryCompiler;
import run.endive.wasm.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EndiveWasmEngine implements WasmEngine {
    private static final Logger LOG = LoggerFactory.getLogger(EndiveWasmEngine.class);

    @Override
    public WasmModule loadModule(byte[] wasmBytes) {
        LOG.debug("Loading WASM module ({} bytes)", wasmBytes.length);
        var module = Parser.parse(wasmBytes);
        var machineFactory = MachineFactoryCompiler.compile(module);
        LOG.debug("WASM module compiled successfully");
        return new EndiveWasmModule(module, machineFactory);
    }
}

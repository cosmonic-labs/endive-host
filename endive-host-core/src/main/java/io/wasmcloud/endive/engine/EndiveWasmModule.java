package io.wasmcloud.endive.engine;

import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.wasm.Parser;
import run.endive.wasi.WasiOptions;
import run.endive.wasi.WasiPreview1;
import run.endive.wasi.WasiPreview1_ModuleFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Map;

public class EndiveWasmModule implements WasmModule {
    private static final Logger LOG = LoggerFactory.getLogger(EndiveWasmModule.class);
    private final byte[] wasmBytes;

    EndiveWasmModule(byte[] wasmBytes) {
        this.wasmBytes = wasmBytes;
    }

    @Override
    public byte[] invoke(byte[] stdin, Map<String, String> env) {
        var stdinStream = new ByteArrayInputStream(stdin);
        var stdoutStream = new ByteArrayOutputStream();
        var stderrStream = new ByteArrayOutputStream();

        var optionsBuilder = WasiOptions.builder()
                .withStdin(stdinStream)
                .withStdout(stdoutStream)
                .withStderr(stderrStream);

        for (var entry : env.entrySet()) {
            optionsBuilder.withEnvironment(entry.getKey(), entry.getValue());
        }

        var wasi = WasiPreview1.builder().withOptions(optionsBuilder.build()).build();
        var hostFunctions = WasiPreview1_ModuleFactory.toHostFunctions(wasi);

        try {
            var module = Parser.parse(wasmBytes);
            var importValues = ImportValues.builder()
                    .withFunctions(Arrays.asList(hostFunctions))
                    .build();
            var instance = Instance.builder(module)
                    .withImportValues(importValues)
                    .withStart(false)
                    .build();

            var start = instance.export("_start");
            start.apply();
        } catch (Exception e) {
            LOG.debug("Module execution completed (may have called proc_exit): {}", e.getMessage());
        } finally {
            try {
                wasi.close();
            } catch (Exception e) {
                LOG.debug("Error closing WASI: {}", e.getMessage());
            }
        }

        var stderr = stderrStream.toByteArray();
        if (stderr.length > 0) {
            LOG.debug("Module stderr: {}", new String(stderr));
        }

        return stdoutStream.toByteArray();
    }
}

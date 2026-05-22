package io.wasmcloud.endive.engine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public interface WasmModule {
    /**
     * Invoke the module's _start export with CGI-style I/O.
     * @param stdin data to pass as stdin
     * @param env environment variables
     * @return stdout output from the module
     */
    byte[] invoke(byte[] stdin, Map<String, String> env);

    /**
     * Invoke with no stdin.
     */
    default byte[] invoke(Map<String, String> env) {
        return invoke(new byte[0], env);
    }

    /**
     * Invoke with no stdin and no env.
     */
    default byte[] invoke() {
        return invoke(new byte[0], Map.of());
    }
}

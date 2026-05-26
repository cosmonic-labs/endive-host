package io.wasmcloud.endive.examples.vertx;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * A pure-Java route handler that composes Java logic with an in-process
 * wasm call. Takes {@code {"title": "...", "body": "..."}} JSON, assembles
 * a markdown document, invokes the markdown wasm via {@link WasmInvoker}
 * (no HTTP), and wraps the resulting HTML in a Java-generated page
 * envelope with a server-side timestamp.
 *
 * <p>The point of the demo: Java code calls a wasm function as if it were
 * any other library, on the same JVM, no NATS / no operator / no HTTP hop
 * between the two.
 */
public class RenderFunction implements Handler<RoutingContext> {
    private static final Logger LOG = LoggerFactory.getLogger(RenderFunction.class);

    private final WasmInvoker markdown;

    public RenderFunction(WasmInvoker markdown) {
        this.markdown = markdown;
    }

    @Override
    public void handle(RoutingContext rc) {
        JsonObject json;
        try {
            json = rc.body() != null && rc.body().buffer() != null && rc.body().buffer().length() > 0
                    ? rc.body().asJsonObject()
                    : new JsonObject();
        } catch (Exception e) {
            rc.response().setStatusCode(400).end("invalid JSON: " + e.getMessage());
            return;
        }
        String title = json.getString("title", "Untitled");
        String body = json.getString("body", "");

        // Compose the markdown payload in Java, then hand it to the wasm module.
        String md = "# " + title + "\n\n" + body + "\n";

        markdown.invoke(rc.vertx(), md.getBytes(StandardCharsets.UTF_8), Map.of())
                .onSuccess(htmlBytes -> {
                    String rendered = new String(htmlBytes, StandardCharsets.UTF_8);
                    String page = """
                            <!doctype html>
                            <html>
                            <head><meta charset="utf-8"><title>%s</title></head>
                            <body>
                            %s
                            <hr>
                            <footer><small>rendered by java + wasm at %s</small></footer>
                            </body>
                            </html>
                            """.formatted(escapeHtml(title), rendered, Instant.now());
                    rc.response()
                            .putHeader("content-type", "text/html; charset=utf-8")
                            .end(page);
                })
                .onFailure(t -> {
                    LOG.error("render failed", t);
                    rc.fail(t);
                });
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

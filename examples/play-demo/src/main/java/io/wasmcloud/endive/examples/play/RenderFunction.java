package io.wasmcloud.endive.examples.play;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
public class RenderFunction {
    private static final Logger LOG = LoggerFactory.getLogger(RenderFunction.class);

    private final WasmInvoker markdown;

    public RenderFunction(WasmInvoker markdown) {
        this.markdown = markdown;
    }

    public CompletionStage<Result> handle(Http.Request request) {
        JsonNode json;
        try {
            json = request.body() != null ? request.body().asJson() : null;
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    Results.badRequest("invalid JSON: " + e.getMessage()));
        }
        String title = json != null && json.hasNonNull("title") ? json.get("title").asText() : "Untitled";
        String body = json != null && json.hasNonNull("body") ? json.get("body").asText() : "";

        // Compose the markdown payload in Java, then hand it to the wasm module.
        String md = "# " + title + "\n\n" + body + "\n";

        return markdown.invoke(md.getBytes(StandardCharsets.UTF_8), Map.of())
                .thenApply(htmlBytes -> {
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
                    return Results.ok(page).as("text/html; charset=utf-8");
                })
                .exceptionally(t -> {
                    LOG.error("render failed", t);
                    return Results.internalServerError("render failed: " + t.getMessage());
                });
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

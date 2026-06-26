package io.wasmcloud.endive.examples.play;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Results;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Avro-encoded sibling of {@link RenderFunction}. The request body is an Avro
 * binary {@code RenderRequest} record; the response body is an Avro binary
 * {@code RenderResponse} record. In between, the same in-process markdown wasm
 * call turns the {@code body} field into HTML — so the wasm composition story
 * is identical, only the wire format on the edges is Avro instead of JSON.
 *
 * <p>Schemas are parsed from inline JSON at startup and records are read/written
 * as {@link GenericRecord} — no generated classes, no Avro codegen plugin.
 */
public class AvroRenderFunction {
    private static final Logger LOG = LoggerFactory.getLogger(AvroRenderFunction.class);

    public static final String CONTENT_TYPE = "application/avro";

    static final Schema REQUEST_SCHEMA = new Schema.Parser().parse("""
            {
              "type": "record",
              "name": "RenderRequest",
              "namespace": "io.wasmcloud.endive.examples.avro",
              "fields": [
                {"name": "title", "type": "string"},
                {"name": "body",  "type": "string"}
              ]
            }
            """);

    static final Schema RESPONSE_SCHEMA = new Schema.Parser().parse("""
            {
              "type": "record",
              "name": "RenderResponse",
              "namespace": "io.wasmcloud.endive.examples.avro",
              "fields": [
                {"name": "html",       "type": "string"},
                {"name": "renderedAt", "type": "string"}
              ]
            }
            """);

    private final WasmInvoker markdown;

    public AvroRenderFunction(WasmInvoker markdown) {
        this.markdown = markdown;
    }

    public CompletionStage<Result> handle(Http.Request request) {
        GenericRecord req;
        try {
            req = decode(WasmFunction.bodyBytes(request));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    Results.badRequest("invalid Avro RenderRequest: " + e.getMessage()));
        }
        String title = String.valueOf(req.get("title"));
        String mdBody = String.valueOf(req.get("body"));

        // Compose the markdown payload in Java, then hand it to the wasm module.
        String md = "# " + title + "\n\n" + mdBody + "\n";

        return markdown.invoke(md.getBytes(StandardCharsets.UTF_8), Map.of())
                .thenApply(htmlBytes -> {
                    String html = new String(htmlBytes, StandardCharsets.UTF_8);
                    try {
                        byte[] out = encode(html, Instant.now().toString());
                        return Results.ok(out).as(CONTENT_TYPE);
                    } catch (Exception e) {
                        LOG.error("Avro encode failed", e);
                        return Results.internalServerError("avro encode failed: " + e.getMessage());
                    }
                })
                .exceptionally(t -> {
                    LOG.error("avro render failed", t);
                    return Results.internalServerError("avro render failed: " + t.getMessage());
                });
    }

    static GenericRecord decode(byte[] bytes) throws Exception {
        var reader = new GenericDatumReader<GenericRecord>(REQUEST_SCHEMA);
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(bytes, null);
        return reader.read(null, decoder);
    }

    static byte[] encode(String html, String renderedAt) throws Exception {
        GenericRecord response = new GenericData.Record(RESPONSE_SCHEMA);
        response.put("html", html);
        response.put("renderedAt", renderedAt);

        var out = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
        var writer = new GenericDatumWriter<GenericRecord>(RESPONSE_SCHEMA);
        writer.write(response, encoder);
        encoder.flush();
        return out.toByteArray();
    }
}

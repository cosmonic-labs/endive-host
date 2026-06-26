package io.wasmcloud.endive.examples.vertx;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;
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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

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
public class AvroRenderFunction implements Handler<RoutingContext> {
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

    @Override
    public void handle(RoutingContext rc) {
        byte[] body = rc.body() != null && rc.body().buffer() != null
                ? rc.body().buffer().getBytes()
                : new byte[0];

        GenericRecord request;
        try {
            request = decode(body);
        } catch (Exception e) {
            rc.response().setStatusCode(400).end("invalid Avro RenderRequest: " + e.getMessage());
            return;
        }
        String title = String.valueOf(request.get("title"));
        String mdBody = String.valueOf(request.get("body"));

        // Compose the markdown payload in Java, then hand it to the wasm module.
        String md = "# " + title + "\n\n" + mdBody + "\n";

        markdown.invoke(rc.vertx(), md.getBytes(StandardCharsets.UTF_8), Map.of())
                .onSuccess(htmlBytes -> {
                    String html = new String(htmlBytes, StandardCharsets.UTF_8);
                    try {
                        byte[] out = encode(html, Instant.now().toString());
                        rc.response()
                                .putHeader("content-type", CONTENT_TYPE)
                                .end(Buffer.buffer(out));
                    } catch (Exception e) {
                        LOG.error("Avro encode failed", e);
                        rc.fail(e);
                    }
                })
                .onFailure(t -> {
                    LOG.error("avro render failed", t);
                    rc.fail(t);
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

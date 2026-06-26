# play-demo

A single [Play Framework](https://www.playframework.com) app that exposes
several HTTP "functions" on one embedded server. Routes are declared in Java
with Play's [`RoutingDsl`](https://www.playframework.com/documentation/latest/JavaEmbeddingPlay)
and served by `Server.forRouter` — **no sbt, no `conf/routes` file, no Play
plugin** — so it builds and runs as a plain Maven fat jar, exactly like
[`vertx-demo`](../vertx-demo/).

| Route | Implementation | Source |
| --- | --- | --- |
| `GET /hello` | pure Java handler | [`HelloFunction.java`](src/main/java/io/wasmcloud/endive/examples/play/HelloFunction.java) |
| `GET /time` | pure Java handler | [`TimeFunction.java`](src/main/java/io/wasmcloud/endive/examples/play/TimeFunction.java) |
| `GET\|POST /wasm/hello` | WASI Preview 1 module via embedded `endive-host-core`; 166-byte hand-written WAT | [`WasmFunction.java`](src/main/java/io/wasmcloud/endive/examples/play/WasmFunction.java) |
| `POST /wasm/markdown` | WASI Preview 1 module via embedded `endive-host-core`; Rust + `pulldown-cmark` | [`../wasm-modules/markdown-to-html/`](../wasm-modules/markdown-to-html/) |
| `POST /render` | Java handler that calls the markdown wasm **in-process** and wraps the HTML in a Java-generated page envelope | [`RenderFunction.java`](src/main/java/io/wasmcloud/endive/examples/play/RenderFunction.java) |
| `POST /render/avro` | Same composition, but **Avro binary** on the wire: decodes a `RenderRequest`, runs the markdown wasm, returns a `RenderResponse` | [`AvroRenderFunction.java`](src/main/java/io/wasmcloud/endive/examples/play/AvroRenderFunction.java) |

The wasm functions use the same `WasmEngine` / `WasmModule` types the
standalone host uses — no NATS, no operator, no separate process. The
demo modules ship inside the fat jar on the classpath (`hello.wasm`,
`markdown.wasm`).

## Run

```sh
# from the repo root
mvn -DskipTests -pl examples/play-demo -am package

java -jar examples/play-demo/target/play-demo-0.1.0-SNAPSHOT.jar
# HTTP server listening on :8088
# Routes: GET /hello (java)  GET /time (java)  GET|POST /wasm/hello (endive)

curl http://localhost:8088/hello    # → hello from java
curl http://localhost:8088/time     # → 2026-05-22T18:55:53.050585Z
curl http://localhost:8088/wasm/hello   # → hello world (via embedded endive engine)

curl -X POST http://localhost:8088/wasm/markdown \
  -H 'content-type: text/markdown' \
  --data-binary $'# endive-host\n\nA **JVM** wasmCloud host on [Endive](https://endive.run).'
# → <h1>endive-host</h1><p>A <strong>JVM</strong> wasmCloud host on
#   <a href="https://endive.run">Endive</a>.</p>

curl -X POST http://localhost:8088/render \
  -H 'content-type: application/json' \
  -d '{"title":"Welcome","body":"This is **markdown** with a [link](https://example.com)."}'
# Java assembles the markdown, calls the markdown wasm in-process, wraps the
# HTML in a Java-built page envelope with a server-side timestamp.
```

`PORT` env var overrides the listening port.

> **Note:** send `/wasm/markdown` with an explicit body content type
> (`text/markdown`, `text/plain`, `application/octet-stream`, …). Play parses
> the request body by content type; with no header `curl --data-binary`
> defaults to `application/x-www-form-urlencoded`, which Play decodes as a
> form rather than handing the bytes straight to the module.

## Avro round-trip (`/render/avro`)

[`AvroRenderFunction`](src/main/java/io/wasmcloud/endive/examples/play/AvroRenderFunction.java)
is the same Java-composes-wasm flow as `/render`, but the HTTP body is
[Apache Avro](https://avro.apache.org) **binary** in both directions.
Schemas are parsed from inline JSON at startup and read/written as
`GenericRecord` — no generated classes, no Avro codegen plugin.

```text
RenderRequest  { title: string, body: string }   # request body
RenderResponse { html: string, renderedAt: string } # response body
```

The body is raw Avro binary, so a hand-written `curl` won't produce it.
Encode/decode with any Avro client using the schemas above — e.g. a few
lines of Java with `GenericDatumWriter` / `GenericDatumReader` against
`application/avro`. The same in-process markdown wasm call sits in the
middle; only the wire format on the edges differs from `/render`.

## Rebuilding the wasm modules

`hello.wasm` is a hand-written WAT (see endive's wasm-corpus for the
source). `markdown.wasm` is built from `../wasm-modules/markdown-to-html/`:

```sh
cd ../wasm-modules/markdown-to-html
cargo build --release --target wasm32-wasip1
cp target/wasm32-wasip1/release/markdown-to-html.wasm \
   ../../play-demo/src/main/resources/markdown.wasm
```

## What this shows

- Java functions and wasm functions coexist in the same JVM, on the same
  Play router, served by one embedded HTTP server.
- Embedding `endive-host-core` only needs the `WasmEngine` /
  `WasmModule` types — control-plane bits (NATS, heartbeats, OCI fetch)
  are not on the call path.
- Wasm invocation is blocking, so it runs on a dedicated worker
  `Executor` (handlers return `CompletionStage<Result>`) to keep Play's
  request threads unblocked. The HTTP request body is piped to the
  module's stdin; the module's stdout becomes the HTTP response.
- A Java handler can call a wasm module as if it were any other library
  (`RenderFunction`) — same JVM, no HTTP hop. The reusable bridge is
  [`WasmInvoker`](src/main/java/io/wasmcloud/endive/examples/play/WasmInvoker.java),
  which both the HTTP-fronting `WasmFunction` and the composite
  `RenderFunction` share.

## What this does not show

- Component-model (WASI 0.2) invocation. The engine only runs `_start`
  modules; same constraint as the standalone host.
- The operator/gateway/k3s deploy story. See the parent repo's `deploy/k3s/`
  for that.

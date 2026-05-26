# vertx-demo

A single [Eclipse Vert.x](https://vertx.io) app that exposes three HTTP
"functions" on one shared event loop:

| Route | Implementation | Source |
| --- | --- | --- |
| `GET /hello` | pure Java handler | [`HelloFunction.java`](src/main/java/io/wasmcloud/endive/examples/vertx/HelloFunction.java) |
| `GET /time` | pure Java handler | [`TimeFunction.java`](src/main/java/io/wasmcloud/endive/examples/vertx/TimeFunction.java) |
| `GET\|POST /wasm/hello` | WASI Preview 1 module via embedded `endive-host-core`; 166-byte hand-written WAT | [`WasmFunction.java`](src/main/java/io/wasmcloud/endive/examples/vertx/WasmFunction.java) |
| `POST /wasm/markdown` | WASI Preview 1 module via embedded `endive-host-core`; Rust + `pulldown-cmark` | [`../wasm-modules/markdown-to-html/`](../wasm-modules/markdown-to-html/) |
| `POST /render` | Java handler that calls the markdown wasm **in-process** and wraps the HTML in a Java-generated page envelope | [`RenderFunction.java`](src/main/java/io/wasmcloud/endive/examples/vertx/RenderFunction.java) |

The wasm functions use the same `WasmEngine` / `WasmModule` types the
standalone host uses — no NATS, no operator, no separate process. The
demo modules ship inside the fat jar on the classpath (`hello.wasm`,
`markdown.wasm`).

## Run

```sh
# from the repo root
mvn -DskipTests -pl examples/vertx-demo -am package

java -jar examples/vertx-demo/target/vertx-demo-0.1.0-SNAPSHOT.jar
# HTTP server listening on :8088
# Routes: GET /hello (java)  GET /time (java)  GET|POST /wasm/hello (endive)

curl http://localhost:8088/hello    # → hello from java
curl http://localhost:8088/time     # → 2026-05-22T18:55:53.050585Z
curl http://localhost:8088/wasm/hello   # → hello world (via embedded endive engine)

curl -X POST http://localhost:8088/wasm/markdown \
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

## Rebuilding the wasm modules

`hello.wasm` is a hand-written WAT (see endive's wasm-corpus for the
source). `markdown.wasm` is built from `../wasm-modules/markdown-to-html/`:

```sh
cd ../wasm-modules/markdown-to-html
cargo build --release --target wasm32-wasip1
cp target/wasm32-wasip1/release/markdown-to-html.wasm \
   ../../vertx-demo/src/main/resources/markdown.wasm
```

## What this shows

- Java functions and wasm functions coexist in the same JVM, on the same
  router, sharing one event loop.
- Embedding `endive-host-core` only needs the `WasmEngine` /
  `WasmModule` types — control-plane bits (NATS, heartbeats, OCI fetch)
  are not on the call path.
- Wasm invocation runs on a Vert.x worker via `executeBlocking` so the
  event loop stays unblocked. The HTTP request body is piped to the
  module's stdin; the module's stdout becomes the HTTP response.
- A Java handler can call a wasm module as if it were any other library
  (`RenderFunction`) — same JVM, no HTTP hop. The reusable bridge is
  [`WasmInvoker`](src/main/java/io/wasmcloud/endive/examples/vertx/WasmInvoker.java),
  which both the HTTP-fronting `WasmFunction` and the composite
  `RenderFunction` share.

## What this does not show

- Component-model (WASI 0.2) invocation. The engine only runs `_start`
  modules; same constraint as the standalone host.
- The operator/gateway/k3s deploy story. See the parent repo's `deploy/k3s/`
  for that.

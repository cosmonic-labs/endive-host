# vertx-demo

A single [Eclipse Vert.x](https://vertx.io) app that exposes three HTTP
"functions" on one shared event loop:

| Route | Implementation | Source |
| --- | --- | --- |
| `GET /hello` | pure Java handler | [`HelloFunction.java`](src/main/java/io/wasmcloud/endive/examples/vertx/HelloFunction.java) |
| `GET /time` | pure Java handler | [`TimeFunction.java`](src/main/java/io/wasmcloud/endive/examples/vertx/TimeFunction.java) |
| `GET\|POST /wasm/hello` | WASI Preview 1 module via embedded `endive-host-core` | [`WasmFunction.java`](src/main/java/io/wasmcloud/endive/examples/vertx/WasmFunction.java) |

The wasm function uses the same `WasmEngine` / `WasmModule` types the
standalone host uses — no NATS, no operator, no separate process. The
demo wasm (a 166-byte `hello world`) ships inside the fat jar at
`hello.wasm` on the classpath.

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
```

`PORT` env var overrides the listening port.

## What this shows

- Java functions and wasm functions coexist in the same JVM, on the same
  router, sharing one event loop.
- Embedding `endive-host-core` only needs the `WasmEngine` /
  `WasmModule` types — control-plane bits (NATS, heartbeats, OCI fetch)
  are not on the call path.
- Wasm invocation runs on a Vert.x worker via `executeBlocking` so the
  event loop stays unblocked. The HTTP request body is piped to the
  module's stdin; the module's stdout becomes the HTTP response.

## What this does not show

- Component-model (WASI 0.2) invocation. The engine only runs `_start`
  modules; same constraint as the standalone host.
- The operator/gateway/k3s deploy story. See the parent repo's `deploy/k3s/`
  for that.

# endive-host

A JVM-based wasmCloud host built on the [Endive](https://endive.run) WebAssembly
runtime (a fork of Chicory). Speaks the wasmCloud runtime-operator NATS API:
heartbeats on `runtime.operator.heartbeat.<host-id>`, RPC on
`runtime.host.<host-id>.workload.{start,status,stop}`.

## Status

What works today, end-to-end:

- Boots, registers as a `Host` CR with the runtime-operator.
- Accepts `WorkloadStartRequest`s dispatched by the operator (or by hand over NATS).
- Pulls wasm bytes from an OCI registry (`Service.image` or
  `WitWorld.components[0].image`), anonymous bearer-token auth, OCI image-index
  and Docker manifest-list both supported, plain-HTTP support for local/dev
  registries via `ENDIVE_INSECURE_REGISTRIES`.
- Binds `wasi:http/incoming-handler` host interfaces to an Undertow HTTP route
  (path from `config.path`, default `/<workload-id>`).
- Also supports running modules from a YAML config file with HTTP, cron, or
  NATS triggers (independent of the operator).

What this host **does not** do:

- Run WASI 0.2 / component-model components. The wasm engine wraps Endive's
  `Instance.builder` and calls `_start`, so modules must be WASI Preview 1
  command modules. A `WorkloadDeployment` declaring `wasi:http/incoming-handler`
  will route to the right wasm and serve it over HTTP, but the *body* of the
  call is a JSON-over-stdio shim — not a real `wasi:http/incoming-handler` host
  binding. Components built against `wasi:http` won't actually be invoked.
- OCI auth beyond anonymous bearer-token. Private registries (most
  `ghcr.io` paths) will return 401. Adding basic auth or a credential
  env var is straightforward.
- Integrate with the wasmCloud runtime-gateway. The host serves HTTP triggers
  directly on its own port; the gateway sees the workload via the operator but
  does not route requests through.

## Quickstart

Prereqs: Docker, Maven 3.9+, JDK 21+, and Endive 999-SNAPSHOT in your local
`~/.m2`. To build Endive locally:

```sh
git clone https://github.com/bytecodealliance/endive ~/repos/bytecodealliance/endive
cd ~/repos/bytecodealliance/endive && ./mvnw install -DskipTests
```

Then build and run the host stack:

```sh
cd ~/repos/cosmonic-labs/endive-host
mvn -DskipTests package
cd deploy/k3s
GATEWAY_PORT=8000 docker compose up -d --build
```

That brings up k3s (with the wasmCloud CRDs already loaded), NATS, an OCI
registry, the wasmCloud runtime-operator, runtime-gateway, and the endive-host
itself. Confirm the host registered:

```sh
export KUBECONFIG=$PWD/tmp/kubeconfig.yaml
kubectl --insecure-skip-tls-verify get hosts.runtime.wasmcloud.dev
# NAMESPACE   NAME          HOSTID                                 HOSTGROUP   READY
# default     endive-host   ac346f7d-5230-4728-9116-6f6bdcdabff1   default     True
```

## Deploying a wasm module

The host accepts WASI Preview 1 command modules (anything exporting `_start`).
A 166-byte `hello world` example lives in `examples/hello.wasm` (copied from
endive's `wasm-corpus`).

Push it to the local registry:

```sh
cd examples
oras push --plain-http localhost:5050/hello:demo \
  hello.wasm:application/vnd.wasm.content.layer.v1+wasm
```

Apply a Workload that points at it:

```sh
kubectl --insecure-skip-tls-verify apply -f examples/workload.yaml
# workload.runtime.wasmcloud.dev/hello created

kubectl --insecure-skip-tls-verify get workload hello \
  -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}'
# True

curl http://localhost:8081/hi
# hello world
```

The operator picks a Ready host, NATS-publishes `WorkloadStartRequest` on
`runtime.host.<host-id>.workload.start`, and the host pulls the image and
registers the HTTP trigger.

## Configuration

### CLI flags (`endive-host-app`)

| flag | default | notes |
| --- | --- | --- |
| `--config`, `-c <path>` | — | YAML config file (see below); CLI flags override |
| `--nats-url <url>` | `nats://localhost:4222` | |
| `--http-port <port>` | `8080` | |
| `--host-group <group>` | `default` | sets the `hostgroup` label |
| `--host-id <id>` | random UUID | |
| `--host-name <name>` | random `<adjective>-<noun>-<nnnn>` | |

### Env vars

| var | notes |
| --- | --- |
| `ENDIVE_INSECURE_REGISTRIES` | comma-separated list of `host:port` entries the OCI fetcher should use plain HTTP for (default empty → HTTPS for everything) |

### YAML config

```yaml
host:
  friendly-name: demo-host
  host-group: default
  labels:
    zone: dev
nats:
  url: nats://nats:4222
http:
  port: 8080
  bind-address: 0.0.0.0
heartbeat:
  interval-seconds: 15
workloads:                          # optional — pre-configured workloads
  - id: hello
    wasm-path: examples/hello.wasm  # or an OCI ref like registry:5000/hello:demo
    trigger:
      type: http                    # http | cron | nats
      path: /hello                  # http: path; cron: schedule; nats: subject
    env:
      FOO: bar
```

The YAML path runs alongside the operator-driven path. Both register triggers
through the same `HttpServer` / `TriggerRegistry`.

## Architecture

```
                    +--------------------+
                    | runtime-operator   |
                    +---------+----------+
                              | NATS: runtime.host.<id>.workload.start
                              v
+--------+   heartbeats   +---+------------+   OCI pull   +-----------+
|  NATS  | <------------- | endive-host    | -----------> | registry  |
+--------+   workload     | (this repo)    |              +-----------+
             RPC          +---+------------+
                              | Undertow trigger
                              v
                    +--------------------+
                    | wasm module        |
                    | (WASI Preview 1)   |
                    +--------------------+
```

The host process is one JVM with five long-lived pieces:

- **`NatsControlPlane`** — subscribes to `runtime.host.<id>.>`, dispatches
  to `HostApi` methods; publishes `HostHeartbeat` every 15s.
- **`HttpServer`** — single Undertow listener with a dynamic
  `path → TriggerCallback` map.
- **`WorkloadManager`** / **`TriggerRegistry`** — register/teardown workloads
  and their associated triggers.
- **`OciFetcher`** — Docker Registry v2 client over `java.net.http`.
- **`EndiveWasmEngine`** / **`EndiveWasmModule`** — wraps Endive's `Parser` +
  `Instance` for WASI Preview 1 execution.

## Repo layout

```
endive-host/
├── endive-host-core/        # library: host runtime, OCI fetch, triggers, NATS
│   └── src/main/proto/      # wasmcloud.runtime.v2 protobuf (host_service,
│                            #   workload_service, host_heartbeat, etc.)
├── endive-host-app/         # executable: CLI parsing + main()
├── deploy/k3s/              # docker-compose stack (k3s + NATS + registry +
│   ├── docker-compose.yml   #   operator + gateway + endive-host)
│   └── kubernetes/          # k3s server scripts, healthcheck
├── charts/runtime-operator/crds/   # synced from upstream chart, pinned in
│                                   #   .version (see scripts/sync-crds.sh)
├── examples/
│   ├── hello.wasm           # WASI Preview 1 "hello world" demo module
│   ├── host.yaml            # YAML config example for the standalone path
│   ├── workload.yaml        # Kubernetes Workload CR example
│   └── vertx-demo/          # Vert.x app embedding endive-host alongside
│                            #   pure-Java handler functions
├── scripts/sync-crds.sh     # pulls CRDs from the published Helm chart
└── Dockerfile               # eclipse-temurin:21-jre-alpine + shaded jar
```

## Syncing CRDs

The committed CRDs in `charts/runtime-operator/crds/` are sourced from the
published Helm chart at `oci://ghcr.io/wasmcloud/charts/runtime-operator`.
Update with:

```sh
scripts/sync-crds.sh                 # uses the version pinned in the script
scripts/sync-crds.sh --version 2.3.0 # bump
```

The synced version is recorded in `charts/runtime-operator/crds/.version`.

## Building

```sh
mvn -DskipTests package
# endive-host-app/target/endive-host-app-0.1.0-SNAPSHOT.jar (shaded fat jar)

# rebuild just the host container in the running stack:
cd deploy/k3s && docker compose up -d --build endive-host
```

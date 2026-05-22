package io.wasmcloud.endive.http;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.wasmcloud.endive.trigger.HttpTriggerEvent;
import io.wasmcloud.endive.trigger.TriggerCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HttpServer {
    private static final Logger LOG = LoggerFactory.getLogger(HttpServer.class);

    private final String bindAddress;
    private final int port;
    private final ConcurrentHashMap<String, TriggerCallback> handlers = new ConcurrentHashMap<>();
    private volatile Undertow server;

    public HttpServer(String bindAddress, int port) {
        this.bindAddress = bindAddress;
        this.port = port;
    }

    public void start() {
        server = Undertow.builder()
                .addHttpListener(port, bindAddress)
                .setHandler(this::handleRequest)
                .build();
        server.start();
        LOG.info("HTTP server started on {}:{}", bindAddress, port);
    }

    public void stop() {
        if (server != null) {
            server.stop();
            LOG.info("HTTP server stopped");
        }
    }

    public int port() {
        return port;
    }

    public void registerHandler(String path, TriggerCallback callback) {
        handlers.put(path, callback);
    }

    public void removeHandler(String path) {
        handlers.remove(path);
    }

    private void handleRequest(HttpServerExchange exchange) {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this::handleRequest);
            return;
        }

        exchange.getRequestReceiver().receiveFullBytes((ex, bytes) -> {
            var path = ex.getRequestPath();
            var callback = findCallback(path);

            if (callback == null) {
                ex.setStatusCode(404);
                ex.getResponseSender().send("Not Found");
                return;
            }

            var headers = new HashMap<String, String>();
            ex.getRequestHeaders().forEach(header ->
                    headers.put(header.getHeaderName().toString(), header.getFirst()));

            var event = new HttpTriggerEvent(
                    ex.getRequestMethod().toString(),
                    path,
                    headers,
                    bytes
            );

            try {
                var response = callback.onTrigger(event);
                ex.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/octet-stream");
                if (response != null && response.length > 0) {
                    ex.getResponseSender().send(ByteBuffer.wrap(response));
                } else {
                    ex.setStatusCode(204);
                    ex.getResponseSender().send("");
                }
            } catch (Exception e) {
                LOG.error("Error handling HTTP request for path {}", path, e);
                ex.setStatusCode(500);
                ex.getResponseSender().send("Internal Server Error");
            }
        });
    }

    private TriggerCallback findCallback(String path) {
        var callback = handlers.get(path);
        if (callback != null) return callback;

        // Try prefix matching for paths like /webhook/*
        for (var entry : handlers.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}

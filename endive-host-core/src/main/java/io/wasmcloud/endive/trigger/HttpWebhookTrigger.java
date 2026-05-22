package io.wasmcloud.endive.trigger;

import io.wasmcloud.endive.http.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpWebhookTrigger implements TriggerSource {
    private static final Logger LOG = LoggerFactory.getLogger(HttpWebhookTrigger.class);

    private final String triggerId;
    private final String pathPattern;
    private final HttpServer httpServer;

    public HttpWebhookTrigger(String triggerId, String pathPattern, HttpServer httpServer) {
        this.triggerId = triggerId;
        this.pathPattern = pathPattern;
        this.httpServer = httpServer;
    }

    @Override
    public String id() {
        return triggerId;
    }

    @Override
    public void start(TriggerCallback callback) {
        httpServer.registerHandler(pathPattern, callback);
        LOG.info("HTTP webhook trigger {} registered at path {}", triggerId, pathPattern);
    }

    @Override
    public void stop() {
        httpServer.removeHandler(pathPattern);
        LOG.info("HTTP webhook trigger {} stopped", triggerId);
    }
}

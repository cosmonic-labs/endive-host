package io.wasmcloud.endive.trigger;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatsEventTrigger implements TriggerSource {
    private static final Logger LOG = LoggerFactory.getLogger(NatsEventTrigger.class);

    private final String triggerId;
    private final String subject;
    private final Connection natsConnection;
    private volatile Dispatcher dispatcher;

    public NatsEventTrigger(String triggerId, String subject, Connection natsConnection) {
        this.triggerId = triggerId;
        this.subject = subject;
        this.natsConnection = natsConnection;
    }

    @Override
    public String id() {
        return triggerId;
    }

    @Override
    public void start(TriggerCallback callback) {
        dispatcher = natsConnection.createDispatcher(msg -> {
            try {
                LOG.debug("NATS event trigger {} received message on {}", triggerId, msg.getSubject());
                var event = new NatsMessageEvent(msg.getSubject(), msg.getData());
                var response = callback.onTrigger(event);
                if (msg.getReplyTo() != null && response != null && response.length > 0) {
                    natsConnection.publish(msg.getReplyTo(), response);
                }
            } catch (Exception e) {
                LOG.error("Error in NATS event trigger {} callback", triggerId, e);
            }
        });
        dispatcher.subscribe(subject);
        LOG.info("NATS event trigger {} subscribed to {}", triggerId, subject);
    }

    @Override
    public void stop() {
        if (dispatcher != null) {
            dispatcher.unsubscribe(subject);
            natsConnection.closeDispatcher(dispatcher);
        }
        LOG.info("NATS event trigger {} stopped", triggerId);
    }
}

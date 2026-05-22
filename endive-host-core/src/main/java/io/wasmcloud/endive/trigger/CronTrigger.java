package io.wasmcloud.endive.trigger;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CronTrigger implements TriggerSource {
    private static final Logger LOG = LoggerFactory.getLogger(CronTrigger.class);

    private final String triggerId;
    private final String schedule;
    private final ScheduledExecutorService executor;
    private volatile ScheduledFuture<?> future;

    public CronTrigger(String triggerId, String schedule) {
        this.triggerId = triggerId;
        this.schedule = schedule;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = Thread.ofVirtual().unstarted(r);
            t.setName("cron-" + triggerId);
            return t;
        });
    }

    @Override
    public String id() {
        return triggerId;
    }

    @Override
    public void start(TriggerCallback callback) {
        var parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
        var cron = parser.parse(schedule);
        cron.validate();
        var executionTime = ExecutionTime.forCron(cron);

        scheduleNext(executionTime, callback);
        LOG.info("Cron trigger {} started with schedule: {}", triggerId, schedule);
    }

    private void scheduleNext(ExecutionTime executionTime, TriggerCallback callback) {
        var now = ZonedDateTime.now();
        var nextExecution = executionTime.nextExecution(now);
        if (nextExecution.isEmpty()) {
            LOG.warn("No next execution time for cron trigger {}", triggerId);
            return;
        }

        var delay = Duration.between(now, nextExecution.get());
        future = executor.schedule(() -> {
            try {
                LOG.debug("Cron trigger {} firing", triggerId);
                callback.onTrigger(new CronTriggerEvent());
            } catch (Exception e) {
                LOG.error("Error in cron trigger {} callback", triggerId, e);
            }
            scheduleNext(executionTime, callback);
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        if (future != null) {
            future.cancel(false);
        }
        executor.shutdown();
        LOG.info("Cron trigger {} stopped", triggerId);
    }
}

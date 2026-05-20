package dev.springboot4docs.ch_41_graceful_shutdown;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
class ShutdownPhaseLogger implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ShutdownPhaseLogger.class);

    private final AtomicBoolean running = new AtomicBoolean();

    private final AtomicBoolean stopped = new AtomicBoolean();

    @Override
    public void start() {
        this.running.set(true);
        log.info("SmartLifecycle bean started in phase {}", getPhase());
    }

    @Override
    public void stop(Runnable callback) {
        log.info("SmartLifecycle stop fired in phase {}", getPhase());
        this.stopped.set(true);
        this.running.set(false);
        callback.run();
    }

    @Override
    public void stop() {
        stop(() -> {
        });
    }

    @Override
    public boolean isRunning() {
        return this.running.get();
    }

    @Override
    public int getPhase() {
        return SmartLifecycle.DEFAULT_PHASE;
    }

    boolean hasStopped() {
        return this.stopped.get();
    }

}

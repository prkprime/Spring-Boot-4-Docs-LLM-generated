package dev.springboot4docs.ch_15_async_sse_virtual_threads;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;

@RestController
class JobsController {

    private final ScheduledExecutorService events = Executors.newSingleThreadScheduledExecutor();

    @GetMapping("/jobs/{id}")
    CompletableFuture<Job> job(@PathVariable Long id) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(200);
            return new Job(id, "complete");
        });
    }

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events() {
        SseEmitter emitter = new SseEmitter(30_000L);
        AtomicInteger counter = new AtomicInteger();
        AtomicReference<ScheduledFuture<?>> task = new AtomicReference<>();

        Runnable sendTick = () -> {
            int tick = counter.incrementAndGet();
            try {
                emitter.send(SseEmitter.event()
                        .name("tick")
                        .data("tick-" + tick));
                if (tick == 5) {
                    emitter.complete();
                }
            }
            catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        };

        emitter.onCompletion(() -> cancel(task.get()));
        emitter.onTimeout(() -> cancel(task.get()));
        emitter.onError((ex) -> cancel(task.get()));

        task.set(this.events.scheduleAtFixedRate(sendTick, 0, 1, TimeUnit.SECONDS));

        return emitter;
    }

    @PreDestroy
    void shutdown() {
        this.events.shutdown();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while loading job", ex);
        }
    }

    private static void cancel(ScheduledFuture<?> task) {
        if (task != null) {
            task.cancel(false);
        }
    }

}

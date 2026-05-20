package dev.springboot4docs.ch_44_virtual_threads;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoadController {

    @GetMapping("/load")
    LoadReport load(@RequestParam(defaultValue = "100") int requests,
            @RequestParam(defaultValue = "100") long sleepMs) throws InterruptedException, ExecutionException {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        int platformThreadsBefore = threads.getThreadCount();
        Instant started = Instant.now();
        List<String> samples = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures = new ArrayList<>(requests);
            for (int i = 0; i < requests; i++) {
                futures.add(executor.submit(sleepingTask(sleepMs)));
            }
            for (Future<String> future : futures) {
                if (samples.size() < 5) {
                    samples.add(future.get());
                }
                else {
                    future.get();
                }
            }
        }

        long durationMs = Duration.between(started, Instant.now()).toMillis();
        int platformThreadsAfter = threads.getThreadCount();
        return new LoadReport(requests, sleepMs, durationMs, platformThreadsBefore, platformThreadsAfter, samples);
    }

    private Callable<String> sleepingTask(long sleepMs) {
        return () -> {
            Thread.sleep(sleepMs);
            return Thread.currentThread().toString();
        };
    }

    record LoadReport(int requests, long sleepMs, long durationMs, int platformThreadsBefore,
            int platformThreadsAfter, List<String> sampleThreads) {
    }

}

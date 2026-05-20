package dev.springboot4docs.ch_39_actuator;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
class BackgroundJobHealthIndicator implements HealthIndicator {

    private static final int MAX_HEALTHY_BACKLOG = 100;

    private final BackgroundJobQueue queue;

    BackgroundJobHealthIndicator(BackgroundJobQueue queue) {
        this.queue = queue;
    }

    @Override
    public Health health() {
        int backlog = this.queue.backlog();

        if (backlog > MAX_HEALTHY_BACKLOG) {
            return Health.down()
                    .withDetail("backlog", backlog)
                    .withDetail("threshold", MAX_HEALTHY_BACKLOG)
                    .build();
        }

        return Health.up()
                .withDetail("backlog", backlog)
                .withDetail("threshold", MAX_HEALTHY_BACKLOG)
                .build();
    }

}

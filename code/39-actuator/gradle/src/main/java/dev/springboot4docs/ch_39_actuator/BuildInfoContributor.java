package dev.springboot4docs.ch_39_actuator;

import java.time.Instant;
import java.util.Map;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
class BuildInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("build", Map.of(
                "artifact", "39-actuator",
                "java", "25",
                "time", Instant.parse("2026-05-10T00:00:00Z")));
    }

}

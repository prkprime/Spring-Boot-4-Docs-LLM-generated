package dev.springboot4docs.ch_45_docker;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class BuildController {

    private final String applicationName;

    BuildController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/api/build")
    BuildInfo buildInfo() {
        return new BuildInfo(
                this.applicationName,
                Runtime.version().feature(),
                Instant.parse("2026-05-10T00:00:00Z"));
    }

    record BuildInfo(String application, int javaFeatureVersion, Instant builtAt) {
    }

}

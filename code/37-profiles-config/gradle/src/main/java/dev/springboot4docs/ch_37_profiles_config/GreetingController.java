package dev.springboot4docs.ch_37_profiles_config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GreetingController {

    private final AppProperties app;

    GreetingController(AppProperties app) {
        this.app = app;
    }

    @GetMapping("/greet")
    String greet() {
        return this.app.greeting();
    }

    @GetMapping("/info")
    AppProperties info() {
        return this.app;
    }

}

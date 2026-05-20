package dev.springboot4docs.ch_39_actuator;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class WidgetController {

    @GetMapping("/widgets")
    List<Widget> widgets() {
        return List.of(new Widget(1, "demo-widget"));
    }

    record Widget(long id, String name) {
    }

}

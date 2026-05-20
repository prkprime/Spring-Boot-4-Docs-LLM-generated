package dev.springboot4docs.ch_31_csrf_headers;

import java.util.List;
import java.util.Map;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class WidgetController {

    @GetMapping("/widgets")
    List<Widget> widgets() {
        return List.of(new Widget(1, "spring"));
    }

    @PostMapping("/widgets")
    Widget createWidget() {
        return new Widget(2, "boot");
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of(
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName(),
                "token", csrfToken.getToken());
    }

    record Widget(long id, String name) {
    }

}

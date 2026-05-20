package dev.springboot4docs.ch_26_fundamentals;

import java.security.Principal;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HelloController {

    @GetMapping("/")
    String root() {
        return "public root";
    }

    @GetMapping("/public/info")
    String publicInfo() {
        return "public info";
    }

    @GetMapping("/me")
    String me(Principal principal) {
        return principal.getName();
    }

}

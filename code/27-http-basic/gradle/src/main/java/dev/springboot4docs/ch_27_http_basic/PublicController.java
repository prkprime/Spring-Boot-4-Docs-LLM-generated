package dev.springboot4docs.ch_27_http_basic;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PublicController {

    @GetMapping("/")
    String root() {
        return "public root";
    }

    @GetMapping("/public/info")
    String publicInfo() {
        return "public info";
    }

}

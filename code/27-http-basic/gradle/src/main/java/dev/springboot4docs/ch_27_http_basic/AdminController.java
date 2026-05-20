package dev.springboot4docs.ch_27_http_basic;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AdminController {

    @GetMapping("/admin/secrets")
    String secrets() {
        return "admin secrets";
    }

}

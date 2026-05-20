package dev.springboot4docs.ch_29_jdbc_users;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SignupController {

    private final AppUserRepository users;

    private final PasswordEncoder passwordEncoder;

    SignupController(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/signup")
    ResponseEntity<SignupResponse> signup(@RequestBody SignupRequest request) {
        AppUser user = new AppUser(request.username(), this.passwordEncoder.encode(request.password()), "USER");
        AppUser saved = this.users.save(user);
        return ResponseEntity.created(URI.create("/users/" + saved.getId()))
                .body(new SignupResponse(saved.getUsername()));
    }

    record SignupRequest(String username, String password) {
    }

    record SignupResponse(String username) {
    }

}

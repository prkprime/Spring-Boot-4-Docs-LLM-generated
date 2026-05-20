package dev.springboot4docs.ch_49_passkeys_webauthn;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/passkeys")
class PasskeyController {

    private final PasskeyCeremonyService passkeys;

    PasskeyController(PasskeyCeremonyService passkeys) {
        this.passkeys = passkeys;
    }

    @PostMapping("/register/options")
    @ResponseStatus(HttpStatus.CREATED)
    CeremonyOptions registerOptions(@Valid @RequestBody RegistrationOptionsRequest request) {
        return this.passkeys.startRegistration(request.username(), request.displayName(), request.origin());
    }

    @PostMapping("/register/finish")
    RegistrationResult finishRegistration(@Valid @RequestBody RegistrationFinishRequest request) {
        return this.passkeys.finishRegistration(request);
    }

    @PostMapping("/authenticate/options")
    @ResponseStatus(HttpStatus.CREATED)
    CeremonyOptions authenticateOptions(@Valid @RequestBody AuthenticationOptionsRequest request) {
        return this.passkeys.startAuthentication(request.username(), request.origin());
    }

    @PostMapping("/authenticate/finish")
    AuthenticationResult finishAuthentication(@Valid @RequestBody AuthenticationFinishRequest request) {
        return this.passkeys.finishAuthentication(request);
    }

    record RegistrationOptionsRequest(
            @NotBlank String username,
            @NotBlank String displayName,
            @NotBlank String origin) {
    }

    record AuthenticationOptionsRequest(
            @NotBlank String username,
            @NotBlank String origin) {
    }

    record CeremonyOptions(
            String challenge,
            Instant expiresAt,
            String publicKeyOptionsJson) {
    }

    record RegistrationFinishRequest(
            @NotBlank String username,
            @NotBlank String origin,
            @NotBlank String challenge,
            @NotBlank String credentialId,
            @NotBlank String publicKeyCose,
            @PositiveOrZero long signCount) {
    }

    record AuthenticationFinishRequest(
            @NotBlank String username,
            @NotBlank String origin,
            @NotBlank String challenge,
            @NotBlank String credentialId,
            @PositiveOrZero long signCount) {
    }

    record RegistrationResult(
            String username,
            String credentialId,
            List<String> recoveryMethods) {
    }

    record AuthenticationResult(
            String username,
            String credentialId,
            boolean signedIn) {
    }

}

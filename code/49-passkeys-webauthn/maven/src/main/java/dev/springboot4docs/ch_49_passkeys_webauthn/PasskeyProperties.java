package dev.springboot4docs.ch_49_passkeys_webauthn;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@ConfigurationProperties(prefix = "app.passkeys")
@Getter
@Setter
class PasskeyProperties {

	private String relyingPartyId = "localhost";

	private String relyingPartyName = "Spring Boot 4 Passkeys";

	private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:8080"));

	private Duration challengeTtl = Duration.ofMinutes(5);

}

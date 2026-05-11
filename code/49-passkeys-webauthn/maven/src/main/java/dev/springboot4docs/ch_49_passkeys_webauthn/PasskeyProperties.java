package dev.springboot4docs.ch_49_passkeys_webauthn;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.passkeys")
class PasskeyProperties {

	private String relyingPartyId = "localhost";

	private String relyingPartyName = "Spring Boot 4 Passkeys";

	private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:8080"));

	private Duration challengeTtl = Duration.ofMinutes(5);

	public String getRelyingPartyId() {
		return this.relyingPartyId;
	}

	public void setRelyingPartyId(String relyingPartyId) {
		this.relyingPartyId = relyingPartyId;
	}

	public String getRelyingPartyName() {
		return this.relyingPartyName;
	}

	public void setRelyingPartyName(String relyingPartyName) {
		this.relyingPartyName = relyingPartyName;
	}

	public List<String> getAllowedOrigins() {
		return this.allowedOrigins;
	}

	public void setAllowedOrigins(List<String> allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	public Duration getChallengeTtl() {
		return this.challengeTtl;
	}

	public void setChallengeTtl(Duration challengeTtl) {
		this.challengeTtl = challengeTtl;
	}

}

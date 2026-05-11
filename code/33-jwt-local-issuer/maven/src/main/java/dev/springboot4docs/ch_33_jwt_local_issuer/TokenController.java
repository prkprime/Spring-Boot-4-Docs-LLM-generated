package dev.springboot4docs.ch_33_jwt_local_issuer;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TokenController {

	private static final String CLIENT_ID = "demo-client";
	private static final String CLIENT_SECRET = "demo-secret";

	private final RSAKey rsaKey;
	private final JWKSource<SecurityContext> jwkSource;

	TokenController(RSAKey rsaKey, JWKSource<SecurityContext> jwkSource) {
		this.rsaKey = rsaKey;
		this.jwkSource = jwkSource;
	}

	@PostMapping("/token")
	ResponseEntity<Map<String, Object>> issueToken(
			@RequestParam("client_id") String clientId,
			@RequestParam("client_secret") String clientSecret,
			@RequestParam(value = "scope", defaultValue = "read") String scope) throws JOSEException {

		if (!CLIENT_ID.equals(clientId) || !CLIENT_SECRET.equals(clientSecret)) {
			return ResponseEntity.status(401).body(Map.of("error", "invalid_client"));
		}

		Instant now = Instant.now();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject(clientId)
				.issuer("self")
				.audience("self")
				.issueTime(Date.from(now))
				.expirationTime(Date.from(now.plusSeconds(3600)))
				.claim("scope", scope)
				.build();

		JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build();
		SignedJWT jwt = new SignedJWT(header, claims);
		jwt.sign(new RSASSASigner(rsaKey.toPrivateKey()));

		return ResponseEntity.ok(Map.of(
				"access_token", jwt.serialize(),
				"token_type", "Bearer",
				"expires_in", 3600,
				"scope", scope));
	}

	@GetMapping("/jwks")
	Map<String, List<Map<String, Object>>> jwks() throws Exception {
		return Map.of("keys", List.of(rsaKey.toPublicJWK().toJSONObject()));
	}

}

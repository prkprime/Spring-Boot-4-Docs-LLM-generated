package dev.springboot4docs.ch_49_passkeys_webauthn;

import org.springframework.data.jpa.repository.JpaRepository;

interface PasskeyChallengeRepository extends JpaRepository<PasskeyChallenge, String> {

}

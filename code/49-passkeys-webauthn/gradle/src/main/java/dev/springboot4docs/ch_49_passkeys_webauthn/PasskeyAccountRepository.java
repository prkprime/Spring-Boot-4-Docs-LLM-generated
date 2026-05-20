package dev.springboot4docs.ch_49_passkeys_webauthn;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface PasskeyAccountRepository extends JpaRepository<PasskeyAccount, Long> {

    Optional<PasskeyAccount> findByUsername(String username);

}

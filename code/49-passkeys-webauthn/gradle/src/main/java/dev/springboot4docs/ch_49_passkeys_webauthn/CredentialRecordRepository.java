package dev.springboot4docs.ch_49_passkeys_webauthn;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface CredentialRecordRepository extends JpaRepository<CredentialRecord, Long> {

    List<CredentialRecord> findByAccountUsername(String username);

    Optional<CredentialRecord> findByCredentialId(String credentialId);

    boolean existsByCredentialId(String credentialId);

}

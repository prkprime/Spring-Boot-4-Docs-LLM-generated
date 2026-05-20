package dev.springboot4docs.ch_35_oauth2_keycloak;

import dasniko.testcontainers.keycloak.KeycloakContainer;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    static final KeycloakContainer KEYCLOAK = new KeycloakContainer()
            .withRealmImportFile("/keycloak/sb4-docs-realm.json");

    static String issuerUri(String realm) {
        KEYCLOAK.start();
        return KEYCLOAK.getAuthServerUrl() + "/realms/" + realm;
    }

    @Bean
    KeycloakContainer keycloakContainer() {
        return KEYCLOAK;
    }

}

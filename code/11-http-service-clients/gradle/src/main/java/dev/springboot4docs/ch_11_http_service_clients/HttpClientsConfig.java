package dev.springboot4docs.ch_11_http_service_clients;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@ImportHttpServices(types = GitHubClient.class)
class HttpClientsConfig {
}

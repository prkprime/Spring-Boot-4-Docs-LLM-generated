package dev.springboot4docs.ch_11_http_service_clients;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange(url = "${spring.http.client.service.dev.springboot4docs.ch_11_http_service_clients.GitHubClient.base-url}")
public interface GitHubClient {

    @GetExchange("/repos/{owner}/{repo}")
    GitHubRepo getRepo(@PathVariable String owner, @PathVariable String repo);

}

package dev.springboot4docs.ch_11_http_service_clients;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RepoController {

    private final GitHubClient github;

    RepoController(GitHubClient github) {
        this.github = github;
    }

    @GetMapping("/repos/{owner}/{repo}")
    GitHubRepo getRepo(@PathVariable String owner, @PathVariable String repo) {
        return this.github.getRepo(owner, repo);
    }

}

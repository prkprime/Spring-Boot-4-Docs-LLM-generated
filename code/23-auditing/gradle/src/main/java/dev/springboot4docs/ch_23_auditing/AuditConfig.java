package dev.springboot4docs.ch_23_auditing;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
class AuditConfig {

    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.ofNullable(RequestContextHolder.getRequestAttributes())
            .filter(ServletRequestAttributes.class::isInstance)
            .map(ServletRequestAttributes.class::cast)
            .map(ServletRequestAttributes::getRequest)
            .map((request) -> request.getHeader("X-User"))
            .filter((user) -> !user.isBlank())
            .or(() -> Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .map((attributes) -> attributes.getAttribute("currentUser", RequestAttributes.SCOPE_REQUEST))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter((user) -> !user.isBlank()))
            .or(() -> Optional.of("system"));
    }

}

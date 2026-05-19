package com.learn.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Configures the API Gateway as an OAuth2 Resource Server.
 * Incoming JWTs are validated using the public JWKS published by user-service.
 * No shared secret — keys are fetched from /.well-known/jwks.json automatically.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges
                // Allow unauthenticated access to login/register and health endpoints
                .pathMatchers("/api/v1/auth/**", "/actuator/**").permitAll()
                .anyExchange().authenticated()
            )
            // Delegates JWT validation to Spring Security using JWKS from application.yml
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        return http.build();
    }
}

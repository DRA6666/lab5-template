package ru.rsoi.library.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                    .requestMatchers(new AntPathRequestMatcher("/api/v1/authorize"), new AntPathRequestMatcher("/api/v1/callback")).permitAll()
                    .requestMatchers(new AntPathRequestMatcher("/api/**")).authenticated()
                    .anyRequest().permitAll())
                .oauth2ResourceServer(o -> o.jwt(j -> {}))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${OIDC_ISSUER:http://localhost:38090/realms/library}") String issuer,
            @Value("${OIDC_JWKS_URI:http://localhost:38090/realms/library/protocol/openid-connect/certs}") String jwks,
            @Value("${OIDC_AUDIENCE:library-api}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            if (jwt.getExpiresAt() == null || jwt.getSubject() == null || jwt.getSubject().isBlank()
                    || !jwt.getAudience().contains(audience)) {
                return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Required token claims are invalid", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ZERO), new JwtIssuerValidator(issuer), claims));
        return decoder;
    }
}

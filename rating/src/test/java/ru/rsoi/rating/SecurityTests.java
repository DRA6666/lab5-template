package ru.rsoi.rating;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityTests.Probe.class)
class SecurityTests {
    static RSAKey key;
    static HttpServer jwks;
    static AtomicInteger requests = new AtomicInteger();
    static {
        try {
            key = new RSAKeyGenerator(2048).keyID("test-key").generate();
            jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            jwks.createContext("/jwks", exchange -> {
                requests.incrementAndGet();
                byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var stream = exchange.getResponseBody()) { stream.write(body); }
            });
            jwks.start();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("OIDC_ISSUER", () -> "https://test-issuer");
        registry.add("OIDC_JWKS_URI", () -> "http://127.0.0.1:" + jwks.getAddress().getPort() + "/jwks");
        registry.add("OIDC_AUDIENCE", () -> "library-api");
    }
    @AfterAll static void stop() { jwks.stop(0); }
    @Autowired MockMvc mvc;

    @RestController
    static class Probe {
        @GetMapping("/api/security-probe")
        String identity(@AuthenticationPrincipal(expression = "claims['preferred_username'] ?: subject") String user) {
            return user;
        }
    }

    String token(RSAKey signingKey, String issuer, String audience, Instant expiration, Instant notBefore) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).subject("subject-123")
                .audience(audience).claim("preferred_username", "alice");
        if (expiration != null) claims.expirationTime(Date.from(expiration));
        if (notBefore != null) claims.notBeforeTime(Date.from(notBefore));
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims.build());
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }
    String valid() throws Exception {
        return token(key, "https://test-issuer", "library-api", Instant.now().plusSeconds(300), null);
    }
    void unauthorized(String token) throws Exception {
        mvc.perform(get("/api/security-probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
    @Test void missingTokenAndLegacyIdentityAreRejected() throws Exception {
        mvc.perform(get("/api/security-probe").header("X-User-Name", "alice"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/not-a-real-route")).andExpect(status().isUnauthorized());
    }
    @Test void acceptsSignedTokenAndIgnoresSpoofedUsername() throws Exception {
        mvc.perform(get("/api/security-probe").header("Authorization", "Bearer " + valid())
                .header("X-User-Name", "mallory"))
                .andExpect(status().isOk()).andExpect(content().string("alice"));
        int before = requests.get();
        mvc.perform(get("/api/security-probe").header("Authorization", "Bearer " + valid()))
                .andExpect(status().isOk());
        assertEquals(before, requests.get(), "Cached JWKS must avoid a provider request per API call");
    }
    @Test void invalidSignatureIsRejected() throws Exception {
        unauthorized(token(new RSAKeyGenerator(2048).generate(), "https://test-issuer", "library-api",
                Instant.now().plusSeconds(300), null));
    }
    @Test void expiredTokenIsRejected() throws Exception {
        unauthorized(token(key, "https://test-issuer", "library-api", Instant.now().minusSeconds(5), null));
    }
    @Test void missingExpirationIsRejected() throws Exception {
        unauthorized(token(key, "https://test-issuer", "library-api", null, null));
    }
    @Test void futureTokenIsRejected() throws Exception {
        unauthorized(token(key, "https://test-issuer", "library-api",
                Instant.now().plusSeconds(300), Instant.now().plusSeconds(100)));
    }
    @Test void wrongIssuerAndAudienceAreRejected() throws Exception {
        unauthorized(token(key, "https://other-issuer", "library-api", Instant.now().plusSeconds(300), null));
        unauthorized(token(key, "https://test-issuer", "other-api", Instant.now().plusSeconds(300), null));
    }
    @Test void malformedTokenIsRejected() throws Exception { unauthorized("not-a-jwt"); }
    @Test void healthIsPublic() throws Exception {
        mvc.perform(get("/manage/health")).andExpect(status().isOk());
    }
}

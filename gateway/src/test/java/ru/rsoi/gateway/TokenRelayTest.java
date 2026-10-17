package ru.rsoi.gateway;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import ru.rsoi.gateway.config.ClientConfig;
import static org.junit.jupiter.api.Assertions.*;

class TokenRelayTest {
    @Test void relaysTheOriginalTokenToDownstreamServices() throws Exception {
        var authorization = new AtomicReference<String>();
        var legacyUser = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/libraries", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            legacyUser.set(exchange.getRequestHeaders().getFirst("X-User-Name"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            Jwt jwt = Jwt.withTokenValue("original-access-token").header("alg", "RS256")
                    .subject("alice").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
            new ClientConfig().restClient().get()
                    .uri("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/libraries")
                    .retrieve().toBodilessEntity();
            assertEquals("Bearer original-access-token", authorization.get());
            assertNull(legacyUser.get());
        } finally {
            SecurityContextHolder.clearContext();
            server.stop(0);
        }
    }
}

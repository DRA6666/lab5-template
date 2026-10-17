package ru.rsoi.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.*;

@RestController
public class AuthorizationController {
    private final RestClient identityClient = RestClient.create();
    @Value("${OIDC_TOKEN_URI:http://localhost:38090/realms/library/protocol/openid-connect/token}")
    private String tokenUri;

    @PostMapping(value = "/api/v1/authorize",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> authorize(@RequestParam MultiValueMap<String, String> parameters) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        for (String name : new String[]{"grant_type", "username", "password", "scope", "client_id", "client_secret"}) {
            if (parameters.getFirst(name) != null) form.add(name, parameters.getFirst(name));
        }
        if (!form.containsKey("client_id")) form.set("client_id", parameters.getFirst("clientId"));
        if (!form.containsKey("client_secret")) form.set("client_secret", parameters.getFirst("clientSecret"));
        if (!form.containsKey("scope")) form.set("scope", "openid profile email");
        if (!"password".equals(form.getFirst("grant_type"))) {
            return response(400, "{\"error\":\"unsupported_grant_type\"}");
        }
        try {
            var result = identityClient.post().uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().toEntity(String.class);
            return response(result.getStatusCode().value(), result.getBody());
        } catch (RestClientResponseException e) {
            return response(e.getStatusCode().value(), e.getResponseBodyAsString());
        } catch (RestClientException e) {
            return response(503, "{\"error\":\"identity_provider_unavailable\"}");
        }
    }

    private ResponseEntity<String> response(int status, String body) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(body);
    }
}

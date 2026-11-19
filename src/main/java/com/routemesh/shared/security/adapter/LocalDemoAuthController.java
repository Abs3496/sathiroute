package com.routemesh.shared.security.adapter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Local Compose-only convenience login; disabled unless explicitly enabled by the development stack. */
@RestController
@ConditionalOnProperty(name = "routemesh.demo-auth.enabled", havingValue = "true")
class LocalDemoAuthController {
    private static final Pattern SUBJECT = Pattern.compile("[A-Za-z0-9._@+-]{3,160}");

    private final RestClient client = RestClient.create();
    private final String tokenUrl;
    private final List<String> dispatchers;
    private final List<String> admins;
    private final List<String> operations;
    private final List<String> partners;

    LocalDemoAuthController(@Value("${routemesh.demo-auth.token-url:http://oauth:8080/routemesh/token}") String tokenUrl,
                            @Value("${routemesh.security.dispatcher-subjects:}") List<String> dispatchers,
                            @Value("${routemesh.security.admin-subjects:}") List<String> admins,
                            @Value("${routemesh.security.operations-manager-subjects:}") List<String> operations,
                            @Value("${routemesh.security.service-partner-subjects:}") List<String> partners) {
        this.tokenUrl = tokenUrl;
        this.dispatchers = clean(dispatchers);
        this.admins = clean(admins);
        this.operations = clean(operations);
        this.partners = clean(partners);
    }

    @PostMapping("/dev/session")
    Session login(@RequestBody Login request) {
        String role = request.role() == null ? "CUSTOMER" : request.role().trim().toUpperCase(Locale.ROOT);
        String subject = subjectFor(role, request.subject());
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", subject);
        form.add("client_secret", "local-demo-only");
        form.add("scope", "routemesh");
        @SuppressWarnings("unchecked")
        Map<String, Object> token = client.post().uri(tokenUrl)
                .header(HttpHeaders.HOST, "oauth:8080")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Map.class);
        if (token == null || !(token.get("access_token") instanceof String accessToken)) {
            throw new IllegalStateException("Local identity provider did not return an access token");
        }
        return new Session(accessToken, subject, role);
    }

    private String subjectFor(String role, String requestedSubject) {
        return switch (role) {
            case "ADMIN", "DISPATCHER" -> first(dispatchers, admins, "dispatch@routemesh.example.test");
            case "OPERATIONS_MANAGER" -> first(operations, List.of(), null);
            case "SERVICE_PARTNER" -> first(partners, List.of(), null);
            case "CUSTOMER" -> {
                String subject = requestedSubject == null ? "" : requestedSubject.trim();
                if (!SUBJECT.matcher(subject).matches()) {
                    throw new IllegalArgumentException("Enter a valid email or subject for the customer account");
                }
                if (dispatchers.contains(subject) || admins.contains(subject) || operations.contains(subject) || partners.contains(subject)) {
                    throw new IllegalArgumentException("That subject is configured for an operator account");
                }
                yield subject;
            }
            default -> throw new IllegalArgumentException("Choose Customer, Service Partner, Operations, or Admin");
        };
    }

    private static String first(List<String> preferred, List<String> secondary, String fallback) {
        if (!preferred.isEmpty()) return preferred.get(0);
        if (!secondary.isEmpty()) return secondary.get(0);
        if (fallback != null) return fallback;
        throw new IllegalArgumentException("This demo role has no configured subject in the local Compose environment");
    }

    private static List<String> clean(List<String> values) {
        return values == null ? List.of() : values.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    record Login(String role, String subject) { }
    record Session(String accessToken, String subject, String role) { }
}

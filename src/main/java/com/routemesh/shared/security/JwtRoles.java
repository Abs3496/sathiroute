package com.routemesh.shared.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Maps only the platform roles from claims signed by the configured OIDC issuer. */
final class JwtRoles implements Converter<Jwt, AbstractAuthenticationToken> {
    private static final Set<String> ALLOWED = Set.of("ADMIN", "OPERATIONS_MANAGER", "SERVICE_PARTNER", "CUSTOMER", "SHIPPER", "DISPATCHER");
    private final JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

    JwtRoles() {
        var scopes = new JwtGrantedAuthoritiesConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
            for (Object raw : claimRoles(jwt)) {
                if (raw instanceof String name) {
                    String role = name.trim().toUpperCase(Locale.ROOT).replaceFirst("^ROLE_", "");
                    if (ALLOWED.contains(role)) authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }
            return authorities;
        });
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return converter.convert(jwt);
    }

    private static Collection<?> claimRoles(Jwt jwt) {
        Collection<Object> values = new ArrayList<>();
        add(values, jwt.getClaims().get("roles"));
        add(values, jwt.getClaims().get("authorities"));
        Object realm = jwt.getClaims().get("realm_access");
        if (realm instanceof Map<?, ?> map) add(values, map.get("roles"));
        Object resource = jwt.getClaims().get("resource_access");
        if (resource instanceof Map<?, ?> clients) {
            clients.values().forEach(client -> {
                if (client instanceof Map<?, ?> map) add(values, map.get("roles"));
            });
        }
        return values;
    }

    private static void add(Collection<Object> values, Object raw) {
        if (raw instanceof Collection<?> collection) values.addAll(collection);
        else if (raw instanceof String value) values.addAll(java.util.Arrays.asList(value.split("[ ,]")));
    }
}

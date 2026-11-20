package com.routemesh.catalog.application;

import com.routemesh.catalog.application.CatalogStore.Service;
import com.routemesh.shared.error.ApiException;
import com.routemesh.shared.security.Caller;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@org.springframework.stereotype.Service
public class ServiceCatalogService {
    private static final Set<String> CATEGORIES = Set.of("NURSING", "AYURVEDIC_THERAPY", "COUNSELLING");
    private static final Pattern CODE = Pattern.compile("[A-Z0-9][A-Z0-9_-]{1,63}");
    private final CatalogStore store;
    private final Clock clock;

    public ServiceCatalogService(CatalogStore store, Clock clock) { this.store = store; this.clock = clock; }

    public java.util.List<Service> list(Caller caller) {
        caller.requireOperations();
        return store.list();
    }

    public Service create(Caller caller, CreateIn in) {
        caller.requireDispatcher();
        if (in == null) throw ApiException.invalid("request body is required");
        String category = clean(in.category()).toUpperCase(Locale.ROOT);
        String name = clean(in.name());
        if (!CATEGORIES.contains(category)) throw ApiException.invalid("category must be NURSING, AYURVEDIC_THERAPY or COUNSELLING");
        if (name.isBlank() || name.length() > 120) throw ApiException.invalid("name is required and must be at most 120 characters");
        String code = clean(in.serviceCode()).toUpperCase(Locale.ROOT);
        if (code.isBlank()) code = name.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        if (code.length() < 2 || code.length() > 64 || !CODE.matcher(code).matches()) throw ApiException.invalid("serviceCode must contain 2..64 letters, digits, underscores or hyphens");
        String description = clean(in.description());
        if (description.length() > 1000) throw ApiException.invalid("description must be at most 1000 characters");
        try {
            return store.create(UUID.randomUUID(), category, code, name, description, caller.subject(), clock.instant());
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("service-code-already-exists", "Choose a unique service code");
        }
    }

    public Service update(Caller caller, UUID id, UpdateIn in) {
        caller.requireDispatcher();
        if (in == null) throw ApiException.invalid("request body is required");
        Service current = store.find(id).orElseThrow(() -> ApiException.notFound("service"));
        String name = in.name() == null ? current.name() : clean(in.name());
        String description = in.description() == null ? current.description() : clean(in.description());
        if (name.isBlank() || name.length() > 120) throw ApiException.invalid("name is required and must be at most 120 characters");
        if (description.length() > 1000) throw ApiException.invalid("description must be at most 1000 characters");
        store.update(id, name, description, clock.instant());
        if (in.active() != null) store.setActive(id, in.active(), clock.instant());
        return store.find(id).orElseThrow(() -> ApiException.notFound("service"));
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
    public record CreateIn(String category, String serviceCode, String name, String description) { }
    public record UpdateIn(String name, String description, Boolean active) { }
}

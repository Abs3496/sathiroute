package com.routemesh.catalog.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogStore {
    record Service(UUID id, String category, String serviceCode, String name, String description,
                   boolean active, String createdBy, Instant createdAt, Instant updatedAt) { }

    List<Service> list();
    boolean categoryActive(String category);
    boolean offeringPaused(String category, String submittedSkill);
    Optional<Service> find(UUID id);
    Service create(UUID id, String category, String code, String name, String description,
                   String createdBy, Instant now);
    void update(UUID id, String name, String description, Instant now);
    void setActive(UUID id, boolean active, Instant now);
}

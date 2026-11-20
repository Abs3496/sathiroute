package com.routemesh.catalog.adapter;

import com.routemesh.catalog.application.CatalogStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcCatalogStore implements CatalogStore {
    private static final String COLUMNS = "id, category, service_code, name, description, active, created_by, created_at, updated_at";
    private final JdbcTemplate jdbc;

    JdbcCatalogStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public List<Service> list() {
        return jdbc.query("SELECT " + COLUMNS + " FROM care_service_catalogue ORDER BY category, name", JdbcCatalogStore::map);
    }

    @Override public boolean categoryActive(String category) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM care_service_catalogue WHERE category = ? AND service_code = ? AND active = TRUE",
                Integer.class, category, category);
        return count != null && count > 0;
    }

    @Override public boolean offeringPaused(String category, String submittedSkill) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM care_service_catalogue WHERE category = ? AND active = FALSE AND (lower(service_code) = lower(?) OR lower(name) = lower(?))",
                Integer.class, category, submittedSkill, submittedSkill);
        return count != null && count > 0;
    }

    @Override public Optional<Service> find(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM care_service_catalogue WHERE id = ?", JdbcCatalogStore::map, id)
                .stream().findFirst();
    }

    @Override public Service create(UUID id, String category, String code, String name, String description,
                                    String createdBy, Instant now) {
        jdbc.update("INSERT INTO care_service_catalogue (id, category, service_code, name, description, active, created_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, TRUE, ?, ?, ?)",
                id, category, code, name, description, createdBy, Timestamp.from(now), Timestamp.from(now));
        return find(id).orElseThrow();
    }

    @Override public void update(UUID id, String name, String description, Instant now) {
        jdbc.update("UPDATE care_service_catalogue SET name = ?, description = ?, updated_at = ? WHERE id = ?",
                name, description, Timestamp.from(now), id);
    }

    @Override public void setActive(UUID id, boolean active, Instant now) {
        jdbc.update("UPDATE care_service_catalogue SET active = ?, updated_at = ? WHERE id = ?",
                active, Timestamp.from(now), id);
    }

    private static Service map(ResultSet rs, int row) throws SQLException {
        return new Service(rs.getObject("id", UUID.class), rs.getString("category"), rs.getString("service_code"),
                rs.getString("name"), rs.getString("description"), rs.getBoolean("active"), rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }
}

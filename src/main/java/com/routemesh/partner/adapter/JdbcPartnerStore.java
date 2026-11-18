/*
 * Copyright (c) 2026 SathiHomeCare. All Rights Reserved.
 * Project: SathiRoute | Developer: Abhishek Pandey
 * Subject to verification of ownership and applicable third-party licenses.
 */
package com.routemesh.partner.adapter;

import com.routemesh.partner.application.PartnerStore;
import com.routemesh.partner.domain.PartnerAvailability;
import com.routemesh.shared.error.ApiException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcPartnerStore implements PartnerStore {
    private static final String PROFILE_SQL = "select id, auth_subject, hub_id, code, name, email, phone, "
            + "qualification_details, active, available, registration_status, latitude, longitude, "
            + "location_updated_at, service_radius_km, created_at, reviewed_by, reviewed_at from service_partner ";
    private final JdbcClient db;

    JdbcPartnerStore(JdbcClient db) {
        this.db = db;
    }

    @Override
    public boolean centreExists(UUID centreId) {
        return centreId != null && db.sql("select exists(select 1 from hub where id = ?)").param(centreId)
                .query(Boolean.class).single();
    }

    @Override
    public void create(UUID id, String subject, UUID centreId, String code, String name, String email, String phone,
                       String qualificationDetails, double radiusKm, Instant now, List<Skill> skills) {
        db.sql("""
                insert into service_partner (id, auth_subject, hub_id, code, name, email, phone,
                    qualification_details, active, available, service_radius_km, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, false, false, ?, ?)
                """).params(id, subject, centreId, code, name, email, phone, qualificationDetails, radiusKm,
                Timestamp.from(now)).update();
        insertSkills(id, skills);
    }

    @Override
    public Optional<Profile> findBySubject(String subject) {
        return db.sql(PROFILE_SQL + "where auth_subject = ?").param(subject).query(this::mapBase).optional()
                .map(this::enrich);
    }

    @Override
    public Optional<Profile> findById(UUID id) {
        return db.sql(PROFILE_SQL + "where id = ?").param(id).query(this::mapBase).optional().map(this::enrich);
    }

    @Override
    public List<Profile> findAll(String status) {
        var query = status == null ? db.sql(PROFILE_SQL + "order by created_at")
                : db.sql(PROFILE_SQL + "where registration_status = ? order by created_at").param(status);
        var base = query.query(this::mapBase).list();
        return base.stream().map(this::enrich).toList();
    }

    @Override
    public void updateProfile(String subject, String name, String email, String phone, String qualificationDetails,
                              double radiusKm) {
        int count = db.sql("""
                update service_partner set name = ?, email = ?, phone = ?, qualification_details = ?,
                    service_radius_km = ?,
                    registration_status = case when qualification_details is distinct from ? then 'PENDING'
                                               else registration_status end,
                    active = case when qualification_details is distinct from ? then false else active end,
                    available = case when qualification_details is distinct from ? then false else available end,
                    reviewed_by = case when qualification_details is distinct from ? then null else reviewed_by end,
                    reviewed_at = case when qualification_details is distinct from ? then null else reviewed_at end
                where auth_subject = ?
                """).params(name, email, phone, qualificationDetails, radiusKm, qualificationDetails,
                qualificationDetails, qualificationDetails, qualificationDetails, qualificationDetails, subject).update();
        requirePartner(count);
        db.sql("update partner_skill set qualification_verified = false where partner_id = "
                + "(select id from service_partner where auth_subject = ?) and exists "
                + "(select 1 from service_partner where auth_subject = ? and registration_status = 'PENDING')")
                .params(subject, subject).update();
    }

    @Override
    public void replaceSkills(String subject, List<Skill> skills) {
        UUID id = findId(subject).orElseThrow(() -> ApiException.notFound("partner profile"));
        db.sql("delete from partner_skill where partner_id = ?").param(id).update();
        insertSkills(id, skills);
        db.sql("update service_partner set registration_status = 'PENDING', active = false, available = false, "
                + "reviewed_by = null, reviewed_at = null where id = ?").param(id).update();
    }

    @Override
    public void replaceAvailability(String subject, List<PartnerAvailability> availability) {
        UUID id = lockPartner(subject);
        db.sql("delete from partner_availability where partner_id = ?").param(id).update();
        for (var window : availability) {
            db.sql("insert into partner_availability (id, partner_id, day_of_week, starts_at, ends_at) "
                    + "values (?, ?, ?, ?, ?)")
                    .params(UUID.randomUUID(), id, window.dayOfWeek(), Time.valueOf(window.startsAt()),
                            Time.valueOf(window.endsAt())).update();
        }
    }

    @Override
    public void setAvailability(String subject, boolean available) {
        Profile profile = findBySubject(subject).orElseThrow(() -> ApiException.notFound("partner profile"));
        if (available && (!profile.active() || !"APPROVED".equals(profile.registrationStatus()))) {
            throw ApiException.conflict("partner-not-active", "partner must be approved and active to become available");
        }
        int count = db.sql("update service_partner set available = ? where auth_subject = ? "
                        + "and (? = false or (active and registration_status = 'APPROVED'))")
                .params(available, subject, available).update();
        if (count == 0 && findBySubject(subject).isEmpty()) {
            throw ApiException.notFound("partner profile");
        }
        if (count == 0 && available) {
            throw ApiException.conflict("partner-not-active", "partner must be approved and active to become available");
        }
    }

    @Override
    public void setLocation(String subject, double latitude, double longitude, Instant at) {
        int count = db.sql("update service_partner set latitude = ?, longitude = ?, location_updated_at = ?, "
                        + "geo_location = ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography "
                        + "where auth_subject = ? and active and registration_status = 'APPROVED'")
                .params(latitude, longitude, Timestamp.from(at), longitude, latitude, subject).update();
        requirePartner(count);
        Profile p = findBySubject(subject).orElseThrow(() -> ApiException.notFound("partner profile"));
        db.sql("insert into partner_location_history (partner_id, recorded_at, position) "
                + "values (?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography)")
                .params(p.id(), Timestamp.from(at), longitude, latitude).update();
    }

    @Override
    public void decideVerification(UUID partnerId, boolean approved, String adminSubject, Instant at) {
        int count = db.sql("update service_partner set registration_status = ?, reviewed_by = ?, reviewed_at = ?, "
                        + "active = false, available = false where id = ?")
                .params(approved ? "APPROVED" : "REJECTED", adminSubject, Timestamp.from(at),
                        partnerId).update();
        requirePartner(count);
        db.sql("update partner_skill set qualification_verified = ? where partner_id = ?")
                .params(approved, partnerId).update();
    }

    @Override
    public void setActive(UUID partnerId, boolean active) {
        int count = db.sql("update service_partner set active = ?, available = case when ? then available else false end "
                        + "where id = ? and (? = false or registration_status = 'APPROVED')")
                .params(active, active, partnerId, active).update();
        requirePartner(count);
        if (active && findById(partnerId).map(p -> !"APPROVED".equals(p.registrationStatus())).orElse(true)) {
            throw ApiException.conflict("partner-not-approved", "only approved partners can be activated");
        }
    }

    @Override
    public void addTimeOff(String subject, LocalDate date, String reason, Instant now) {
        try {
            lockPartner(subject);
            int count = db.sql("insert into partner_time_off (partner_id, unavailable_date, reason, created_at) "
                            + "select id, ?, ?, ? from service_partner where auth_subject = ?")
                    .params(date, reason, Timestamp.from(now), subject).update();
            requirePartner(count);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw ApiException.conflict("time-off-exists", "unavailable date is already registered");
        }
    }

    @Override
    public void removeTimeOff(String subject, LocalDate date) {
        lockPartner(subject);
        db.sql("delete from partner_time_off t using service_partner p where t.partner_id = p.id "
                + "and p.auth_subject = ? and t.unavailable_date = ?").params(subject, date).update();
    }

    @Override
    public List<LocalDate> timeOff(String subject, LocalDate from, LocalDate to) {
        return db.sql("select t.unavailable_date from partner_time_off t join service_partner p "
                        + "on p.id = t.partner_id where p.auth_subject = ? and t.unavailable_date between ? and ? "
                        + "order by t.unavailable_date")
                .params(subject, from, to).query(LocalDate.class).list();
    }

    @Override
    public List<BookingHistory> history(UUID partnerId, int limit) {
        return db.sql("select id, category, required_skill, appointment_start, appointment_end, status "
                        + "from service_booking where assigned_partner_id = ? order by appointment_start desc limit ?")
                .params(partnerId, limit).query((rs, n) -> new BookingHistory(rs.getObject("id", UUID.class),
                        rs.getString("category"), rs.getString("required_skill"),
                        rs.getTimestamp("appointment_start").toInstant(), rs.getTimestamp("appointment_end").toInstant(),
                        rs.getString("status"))).list();
    }

    @Override
    public Workload workload(UUID partnerId, Instant now) {
        return db.sql("""
                select count(*) filter (where status in ('PARTNER_OFFERED', 'CONFIRMED', 'PARTNER_ON_THE_WAY',
                    'PARTNER_NEARBY', 'PARTNER_ARRIVED', 'IN_PROGRESS') and appointment_end >= ?) as upcoming,
                    count(*) filter (where status = 'COMPLETED') as completed
                from service_booking where assigned_partner_id = ?
                """).params(Timestamp.from(now), partnerId)
                .query((rs, n) -> new Workload(rs.getInt("upcoming"), rs.getLong("completed"))).single();
    }

    private Optional<UUID> findId(String subject) {
        return db.sql("select id from service_partner where auth_subject = ?").param(subject)
                .query(UUID.class).optional();
    }

    private UUID lockPartner(String subject) {
        return db.sql("select id from service_partner where auth_subject = ? for update").param(subject)
                .query(UUID.class).optional().orElseThrow(() -> ApiException.notFound("partner profile"));
    }

    private void insertSkills(UUID partnerId, List<Skill> skills) {
        for (var skill : skills) {
            db.sql("insert into partner_skill (partner_id, service_category, skill_code, qualification_details, qualification_verified) "
                            + "values (?, ?, ?, ?, ?)")
                    .params(partnerId, skill.category(), skill.code(), skill.qualificationDetails(), skill.verified()).update();
        }
    }

    private Profile enrich(Profile base) {
        var skills = db.sql("select service_category, skill_code, qualification_details, qualification_verified "
                        + "from partner_skill where partner_id = ? order by service_category, skill_code")
                .param(base.id()).query((rs, n) -> new Skill(rs.getString("service_category"), rs.getString("skill_code"),
                        rs.getString("qualification_details"), rs.getBoolean("qualification_verified"))).list();
        var availability = db.sql("select day_of_week, starts_at, ends_at from partner_availability "
                        + "where partner_id = ? and active order by day_of_week, starts_at")
                .param(base.id()).query((rs, n) -> new PartnerAvailability(rs.getInt("day_of_week"),
                        rs.getTime("starts_at").toLocalTime(), rs.getTime("ends_at").toLocalTime())).list();
        return new Profile(base.id(), base.subject(), base.centreId(), base.code(), base.name(), base.email(),
                base.phone(), base.qualificationDetails(), base.active(), base.available(), base.registrationStatus(),
                base.latitude(), base.longitude(), base.locationUpdatedAt(), base.serviceRadiusKm(), base.createdAt(),
                base.reviewedBy(), base.reviewedAt(), skills, availability);
    }

    private Profile mapBase(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        Double lat = rs.getObject("latitude", Double.class);
        Double lng = rs.getObject("longitude", Double.class);
        Timestamp locationAt = rs.getTimestamp("location_updated_at");
        Timestamp reviewedAt = rs.getTimestamp("reviewed_at");
        return new Profile(rs.getObject("id", UUID.class), rs.getString("auth_subject"),
                rs.getObject("hub_id", UUID.class), rs.getString("code"), rs.getString("name"),
                rs.getString("email"), rs.getString("phone"), rs.getString("qualification_details"),
                rs.getBoolean("active"), rs.getBoolean("available"), rs.getString("registration_status"), lat, lng,
                locationAt == null ? null : locationAt.toInstant(), rs.getDouble("service_radius_km"),
                rs.getTimestamp("created_at").toInstant(), rs.getString("reviewed_by"),
                reviewedAt == null ? null : reviewedAt.toInstant(), List.of(), List.of());
    }

    private static void requirePartner(int updated) {
        if (updated == 0) {
            throw ApiException.notFound("partner profile");
        }
    }
}

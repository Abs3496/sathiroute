/*
 * Copyright (c) 2026 SathiHomeCare. All Rights Reserved.
 * Project: SathiRoute | Developer: Abhishek Pandey
 * Subject to verification of ownership and applicable third-party licenses.
 */
package com.routemesh.booking.adapter;

import com.routemesh.booking.application.BookingStore;
import com.routemesh.booking.application.BookingAllocationStore;
import com.routemesh.booking.domain.BookingStatus;
import com.routemesh.geo.adapter.Pg;
import com.routemesh.geo.domain.GeoPoint;
import com.routemesh.shared.error.ApiException;
import com.routemesh.outbox.application.Outbox;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcBookingStore implements BookingStore, BookingAllocationStore {
    private static final String CUSTOMER_COLUMNS = "id, auth_subject, full_name, email, phone, created_at, updated_at";
    private static final String BOOKING_COLUMNS = "id, customer_id, customer_subject, customer_name, customer_email, "
            + "contact_phone, category, required_skill, appointment_date, appointment_start, appointment_end, "
            + "service_duration_minutes, address, " + Pg.cols("location", "loc")
            + ", special_instructions, assigned_partner_id, status, version, created_at, updated_at ";

    private final JdbcClient db;
    private final Outbox outbox;

    JdbcBookingStore(JdbcClient db, Outbox outbox) {
        this.db = db;
        this.outbox = outbox;
    }

    @Override
    public List<Booking> pendingBookings(int limit) {
        return db.sql("select " + BOOKING_COLUMNS + "from service_booking where status in ('NEW','PENDING') "
                        + "and appointment_start > now() order by appointment_start, created_at limit ?")
                .param(limit).query(this::mapBooking).list();
    }

    @Override
    public List<UUID> eligiblePartners(UUID bookingId, Instant now, Instant locationCutoff, int maxWorkload,
                                       double roadFactor, double planningKmh, int travelBufferMinutes) {
        return db.sql("""
                select p.id from service_partner p join service_booking b on b.id = ?
                where b.status = 'PENDING' and p.active and p.available and p.registration_status = 'APPROVED'
                  and p.geo_location is not null and p.location_updated_at >= ?
                  and ST_DWithin(p.geo_location, b.location, p.service_radius_km * 1000)
                  and exists (select 1 from partner_skill s where s.partner_id = p.id and s.service_category = b.category
                    and s.skill_code = b.required_skill and s.qualification_verified)
                  and exists (select 1 from partner_availability a where a.partner_id = p.id and a.active
                    and a.day_of_week = extract(isodow from b.appointment_date)::smallint
                    and a.starts_at <= b.appointment_start::time and a.ends_at >= b.appointment_end::time)
                  and not exists (select 1 from partner_time_off t where t.partner_id = p.id and t.unavailable_date = b.appointment_date)
                  and (select count(*) from service_booking w where w.assigned_partner_id = p.id
                    and w.status in ('ASSIGNED','CONFIRMED','PARTNER_ON_THE_WAY','PARTNER_NEARBY','PARTNER_ARRIVED','IN_PROGRESS')
                    and w.appointment_end > ?) < ?
                  and not exists (select 1 from allocation_attempt aa where aa.booking_id = b.id and aa.partner_id = p.id)
                  and not exists (select 1 from service_booking o where o.assigned_partner_id = p.id and o.id <> b.id
                    and o.status in ('ASSIGNED','CONFIRMED','PARTNER_ON_THE_WAY','PARTNER_NEARBY','PARTNER_ARRIVED','IN_PROGRESS') and (
                      (o.appointment_start < b.appointment_end and o.appointment_end > b.appointment_start)
                      or (o.appointment_end <= b.appointment_start and extract(epoch from (b.appointment_start-o.appointment_end))/60 <
                        ST_Distance(o.location,b.location)/1000 * ? / ? * 60 + ?)
                      or (o.appointment_start >= b.appointment_end and extract(epoch from (o.appointment_start-b.appointment_end))/60 <
                        ST_Distance(o.location,b.location)/1000 * ? / ? * 60 + ?)))
                order by ST_Distance(p.geo_location,b.location),
                  (select count(*) from service_booking w where w.assigned_partner_id=p.id
                   and w.status in ('ASSIGNED','CONFIRMED','PARTNER_ON_THE_WAY','PARTNER_NEARBY','PARTNER_ARRIVED','IN_PROGRESS')
                   and w.appointment_end > ?), p.id
                """).params(bookingId, Timestamp.from(locationCutoff), Timestamp.from(now), maxWorkload,
                roadFactor, planningKmh, travelBufferMinutes, roadFactor, planningKmh, travelBufferMinutes,
                Timestamp.from(now)).query(UUID.class).list();
    }

    @Override
    @Transactional
    public boolean offer(UUID bookingId, UUID partnerId, Instant now, Instant expiresAt, String actor) {
        var locked = db.sql("select version from service_booking where id=? and status='PENDING' for update")
                .param(bookingId).query(Long.class).optional();
        if (locked.isEmpty()) return false;
        if (db.sql("select id from service_partner where id=? for update").param(partnerId)
                .query(UUID.class).optional().isEmpty()) return false;
        int attempt = db.sql("select coalesce(max(attempt_number),0)+1 from allocation_attempt where booking_id=?")
                .param(bookingId).query(Integer.class).single();
        int changed = db.sql("update service_booking set assigned_partner_id=?,status='ASSIGNED',version=version+1,updated_at=? where id=? and status='PENDING'")
                .params(partnerId, Timestamp.from(now), bookingId).update();
        if (changed == 0) return false;
        db.sql("insert into allocation_attempt (booking_id,partner_id,attempt_number,status,offered_at,expires_at) values (?,?,?,'OFFERED',?,?)")
                .params(bookingId, partnerId, attempt, Timestamp.from(now), Timestamp.from(expiresAt)).update();
        appendStatus(bookingId, BookingStatus.PENDING, BookingStatus.ASSIGNED, actor, "partner offer sent", now);
        return true;
    }

    @Override
    @Transactional
    public List<UUID> expireOffers(Instant now, String actor) {
        List<UUID> ids = db.sql("select booking_id from allocation_attempt where status='OFFERED' and expires_at <= ? for update")
                .param(Timestamp.from(now)).query(UUID.class).list();
        for (UUID id : ids) {
            db.sql("update allocation_attempt set status='EXPIRED',responded_at=? where booking_id=? and status='OFFERED'")
                    .params(Timestamp.from(now), id).update();
            int changed = db.sql("update service_booking set assigned_partner_id=null,status='PENDING',version=version+1,updated_at=? where id=? and status='ASSIGNED'")
                    .params(Timestamp.from(now), id).update();
            if (changed > 0) appendStatus(id, BookingStatus.ASSIGNED, BookingStatus.PENDING, actor, "partner offer expired", now);
        }
        return ids;
    }

    @Override
    @Transactional
    public Booking respond(UUID bookingId, String partnerSubject, long expectedVersion, boolean accepted, String reason, Instant now) {
        var row = db.sql("select b.version,b.status from service_booking b join service_partner p on p.id=b.assigned_partner_id where b.id=? and p.auth_subject=? for update of b")
                .params(bookingId, partnerSubject).query((rs,n) -> new Object[]{rs.getLong(1),rs.getString(2)}).optional();
        if (row.isEmpty()) throw ApiException.notFound("offered booking");
        if (!"ASSIGNED".equals(row.get()[1])) throw ApiException.conflict("offer-not-open", "booking has no open partner offer");
        if ((long) row.get()[0] != expectedVersion) throw ApiException.conflict("version-conflict", "booking changed; reload and retry");
        String outcome = accepted ? "ACCEPTED" : "REJECTED";
        int attempts = db.sql("update allocation_attempt set status=?,responded_at=?,response_reason=? where booking_id=? and status='OFFERED' and expires_at > ?")
                .params(outcome, Timestamp.from(now), reason, bookingId, Timestamp.from(now)).update();
        if (attempts == 0) throw ApiException.conflict("offer-expired", "partner offer has expired");
        BookingStatus target = accepted ? BookingStatus.CONFIRMED : BookingStatus.PENDING;
        int changed = db.sql("update service_booking set status=?,assigned_partner_id=case when ? then assigned_partner_id else null end,version=version+1,updated_at=? where id=? and version=? and status='ASSIGNED'")
                .params(target.name(), accepted, Timestamp.from(now), bookingId, expectedVersion).update();
        if (changed == 0) throw updateConflict(bookingId, (long) row.get()[0]);
        appendStatus(bookingId, BookingStatus.ASSIGNED, target, partnerSubject,
                reason == null || reason.isBlank() ? (accepted ? "partner accepted offer" : "partner rejected offer") : reason, now);
        return booking(bookingId).orElseThrow();
    }

    @Override
    public boolean hasTried(UUID bookingId, UUID partnerId) {
        return db.sql("select exists(select 1 from allocation_attempt where booking_id=? and partner_id=?)")
                .params(bookingId, partnerId).query(Boolean.class).single();
    }

    @Override
    public int attemptCount(UUID bookingId) {
        return db.sql("select count(*) from allocation_attempt where booking_id=?").param(bookingId)
                .query(Integer.class).single();
    }

    @Override
    @Transactional
    public void exhaust(UUID bookingId, Instant now, String actor) {
        int changed = db.sql("update service_booking set status='REJECTED',version=version+1,updated_at=? "
                        + "where id=? and status='PENDING'")
                .params(Timestamp.from(now), bookingId).update();
        if (changed > 0) appendStatus(bookingId, BookingStatus.PENDING, BookingStatus.REJECTED,
                actor, "allocation offer limit exhausted", now);
    }

    @Override
    public Optional<Customer> customer(String subject) {
        return db.sql("select " + CUSTOMER_COLUMNS + " from customer_profile where auth_subject = ?")
                .param(subject).query((rs, n) -> mapCustomer(rs)).optional();
    }

    @Override
    @Transactional
    public Customer insertCustomer(UUID id, String subject, String name, String email, String phone, Instant now) {
        db.sql("insert into customer_profile (id, auth_subject, full_name, email, phone, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?)")
                .params(id, subject, name, email, phone, Timestamp.from(now), Timestamp.from(now)).update();
        return customer(subject).orElseThrow();
    }

    @Override
    @Transactional
    public void updateCustomer(String subject, String name, String email, String phone, Instant now) {
        int updated = db.sql("update customer_profile set full_name = ?, email = ?, phone = ?, updated_at = ? "
                        + "where auth_subject = ?")
                .params(name, email, phone, Timestamp.from(now), subject).update();
        if (updated == 0) throw ApiException.notFound("customer profile");
    }

    @Override
    @Transactional
    public void insertBooking(Booking booking) {
        var p = booking.location();
        db.sql("""
                insert into service_booking (id, customer_id, customer_subject, customer_name, customer_email,
                    contact_phone, category, required_skill, appointment_date, appointment_start, appointment_end,
                    service_duration_minutes, address, location, special_instructions, status, version, created_at,
                    updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                    ?, ?, ?, ?, ?)
                """).params(booking.id(), booking.customerId(), booking.customerSubject(), booking.customerName(),
                booking.customerEmail(), booking.customerPhone(), booking.category(), booking.requiredSkill(),
                Date.valueOf(booking.appointmentDate()), Timestamp.from(booking.appointmentStartAt()),
                Timestamp.from(booking.appointmentEndAt()), booking.durationMinutes(), booking.address(),
                p.lng(), p.lat(), booking.notes(), booking.status().name(), booking.version(),
                Timestamp.from(booking.createdAt()), Timestamp.from(booking.updatedAt())).update();
        appendStatus(booking.id(), null, booking.status(), booking.customerSubject(), "booking created", booking.createdAt());
    }

    @Override
    public Optional<Booking> booking(UUID id) {
        return db.sql("select " + BOOKING_COLUMNS + "from service_booking where id = ?").param(id)
                .query(this::mapBooking).optional();
    }

    @Override
    public List<Booking> bookings(String subject, int limit, int offset) {
        return db.sql("select " + BOOKING_COLUMNS + "from service_booking where customer_subject = ? "
                        + "order by created_at desc, id limit ? offset ?")
                .params(subject, limit, offset).query(this::mapBooking).list();
    }

    @Override
    public List<Booking> adminBookings(int limit, int offset) {
        return db.sql("select " + BOOKING_COLUMNS + "from service_booking "
                        + "order by appointment_start desc, id limit ? offset ?")
                .params(limit, offset).query(this::mapBooking).list();
    }

    @Override
    public List<Booking> partnerBookings(String partnerSubject, int limit, int offset) {
        return db.sql("select " + BOOKING_COLUMNS + "from service_booking where id in "
                        + "(select b.id from service_booking b join service_partner p "
                        + "on p.id = b.assigned_partner_id where p.auth_subject = ?) "
                        + "order by appointment_start desc, id limit ? offset ?")
                .params(partnerSubject, limit, offset).query(this::mapBooking).list();
    }

    @Override
    public Optional<Booking> partnerBooking(String partnerSubject, UUID bookingId) {
        return db.sql("select " + BOOKING_COLUMNS + "from service_booking where id = ? and id in "
                        + "(select b.id from service_booking b join service_partner p "
                        + "on p.id = b.assigned_partner_id where p.auth_subject = ?)")
                .params(bookingId, partnerSubject).query(this::mapBooking).optional();
    }

    @Override
    @Transactional
    public Booking assign(UUID bookingId, long expectedVersion, UUID partnerId, LocalTime start, LocalTime end,
                          String actorSubject, Instant now) {
        var eligible = db.sql("""
                select p.id from service_partner p
                join service_booking b on b.id = ?
                where p.id = ? and b.status = 'PENDING' and b.version = ?
                  and p.active and p.available and p.registration_status = 'APPROVED'
                  and exists (select 1 from partner_skill s where s.partner_id = p.id
                      and s.service_category = b.category and s.skill_code = b.required_skill
                      and s.qualification_verified)
                  and p.latitude is not null and ST_DWithin(
                      ST_SetSRID(ST_MakePoint(p.longitude, p.latitude), 4326)::geography,
                      b.location, p.service_radius_km * 1000)
                  and exists (select 1 from partner_availability a where a.partner_id = p.id and a.active
                      and a.day_of_week = extract(isodow from b.appointment_date)::smallint
                      and a.starts_at <= ? and a.ends_at >= ?)
                  and not exists (select 1 from partner_time_off t where t.partner_id = p.id
                      and t.unavailable_date = b.appointment_date)
                  and not exists (select 1 from service_booking other where other.assigned_partner_id = p.id
                      and other.id <> b.id
                      and other.status in ('ASSIGNED', 'CONFIRMED', 'IN_PROGRESS', 'PARTNER_OFFERED',
                          'PARTNER_ON_THE_WAY', 'PARTNER_NEARBY', 'PARTNER_ARRIVED')
                      and other.appointment_start < b.appointment_end
                      and other.appointment_end > b.appointment_start)
                for update of p
                """).params(bookingId, partnerId, expectedVersion, Time.valueOf(start), Time.valueOf(end))
                .query(UUID.class).optional();
        if (eligible.isEmpty()) {
            var current = booking(bookingId).orElseThrow(() -> ApiException.notFound("booking"));
            if (current.version() != expectedVersion) {
                throw ApiException.conflict("version-conflict", "booking changed; reload and retry");
            }
            throw ApiException.conflict("partner-ineligible",
                    "partner must be approved, active, available, qualified, within radius and schedule, and conflict-free");
        }
        int updated = db.sql("update service_booking set assigned_partner_id = ?, status = 'ASSIGNED', "
                        + "version = version + 1, updated_at = ? where id = ? and version = ? and status = 'PENDING'")
                .params(partnerId, Timestamp.from(now), bookingId, expectedVersion).update();
        if (updated == 0) throw updateConflict(bookingId, expectedVersion);
        appendStatus(bookingId, BookingStatus.PENDING, BookingStatus.ASSIGNED, actorSubject, "partner assigned", now);
        return booking(bookingId).orElseThrow(() -> ApiException.notFound("booking"));
    }

    @Override
    public boolean partnerOwns(String partnerSubject, UUID bookingId) {
        return db.sql("select exists(select 1 from service_booking b join service_partner p "
                        + "on p.id = b.assigned_partner_id where b.id = ? and p.auth_subject = ?)")
                .params(bookingId, partnerSubject).query(Boolean.class).single();
    }

    @Override
    @Transactional
    public Booking transitionAsPartner(UUID id, long expectedVersion, BookingStatus from, BookingStatus to,
                                      String partnerSubject, String reason, Instant now) {
        int updated = db.sql("""
                update service_booking b set status = ?,
                    assigned_partner_id = case when ? = 'REJECTED' then null else b.assigned_partner_id end,
                    version = version + 1, updated_at = ?
                from service_partner p
                where b.id = ? and b.assigned_partner_id = p.id and p.auth_subject = ?
                  and b.version = ? and b.status = ?
                """).params(to.name(), to.name(), Timestamp.from(now), id, partnerSubject, expectedVersion,
                from.name()).update();
        if (updated == 0) {
            if (!partnerOwns(partnerSubject, id)) throw ApiException.notFound("assigned booking");
            throw updateConflict(id, expectedVersion);
        }
        appendStatus(id, from, to, partnerSubject, reason, now);
        return booking(id).orElseThrow(() -> ApiException.notFound("booking"));
    }

    @Override
    @Transactional
    public Booking updateBooking(String subject, UUID id, long expectedVersion, String category, String requiredSkill,
                                LocalDate date, LocalTime start, Instant startAt, Instant endAt, int durationMinutes,
                                String address, GeoPoint location, String notes, Instant now) {
        int updated = db.sql("""
                update service_booking set category = ?, required_skill = ?, appointment_date = ?,
                    appointment_start = ?, appointment_end = ?, service_duration_minutes = ?, address = ?,
                    location = ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, special_instructions = ?,
                    version = version + 1, updated_at = ?
                where id = ? and customer_subject = ? and version = ? and status in ('NEW', 'PENDING')
                """).params(category, requiredSkill, Date.valueOf(date), Timestamp.from(startAt), Timestamp.from(endAt),
                durationMinutes, address, location.lng(), location.lat(), notes, Timestamp.from(now), id, subject,
                expectedVersion).update();
        if (updated == 0) throw updateConflict(id, expectedVersion);
        return booking(id).orElseThrow(() -> ApiException.notFound("booking"));
    }

    @Override
    @Transactional
    public Booking transition(UUID id, long expectedVersion, BookingStatus from, BookingStatus to, String actorSubject,
                              String reason, Instant now) {
        int updated = db.sql("update service_booking set status = ?, "
                        + "assigned_partner_id = case when ? = 'REJECTED' then null else assigned_partner_id end, "
                        + "version = version + 1, updated_at = ? "
                        + "where id = ? and version = ? and status = ?")
                .params(to.name(), to.name(), Timestamp.from(now), id, expectedVersion, from.name()).update();
        if (updated == 0) throw updateConflict(id, expectedVersion);
        appendStatus(id, from, to, actorSubject, reason, now);
        return booking(id).orElseThrow(() -> ApiException.notFound("booking"));
    }

    private RuntimeException updateConflict(UUID id, long expectedVersion) {
        var current = db.sql("select status, version from service_booking where id = ?")
                .param(id).query((rs, n) -> new Object[]{rs.getString("status"), rs.getLong("version")}).optional();
        if (current.isEmpty()) return ApiException.notFound("booking");
        if ((long) current.get()[1] != expectedVersion) {
            return ApiException.conflict("version-conflict", "booking changed; reload and retry with the current version");
        }
        return ApiException.conflict("illegal-booking-transition", "booking is no longer in the expected state");
    }

    private void appendStatus(UUID bookingId, BookingStatus from, BookingStatus to, String actor, String reason,
                              Instant at) {
        db.sql("insert into service_booking_status_history (booking_id, from_status, to_status, actor_subject, reason, "
                        + "occurred_at) values (?, ?, ?, ?, ?, ?)")
                .params(bookingId, from == null ? null : from.name(), to.name(), actor, reason, Timestamp.from(at)).update();
        String eventType = notificationEvent(from, to, reason);
        if (eventType != null) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("bookingId", bookingId.toString());
            data.put("fromStatus", from == null ? null : from.name());
            data.put("status", to.name());
            data.put("actor", actor);
            data.put("reason", reason == null ? "" : reason);
            String partnerId = db.sql("select assigned_partner_id::text from service_booking where id=?")
                    .param(bookingId).query(String.class).optional().orElse(null);
            if (partnerId != null) data.put("partnerId", partnerId);
            if (to == BookingStatus.ASSIGNED) {
                int attempts = db.sql("select count(*) from allocation_attempt where booking_id=?")
                        .param(bookingId).query(Integer.class).single();
                if (attempts > 1) eventType = "PARTNER_REASSIGNED";
            }
            outbox.append(Outbox.SERVICE_EVENTS, bookingId.toString(), eventType, data);
        }
    }

    private static String notificationEvent(BookingStatus from, BookingStatus to, String reason) {
        if (from == null && to == BookingStatus.NEW) return "BOOKING_CREATED";
        if (to == BookingStatus.ASSIGNED) return "PARTNER_ALLOCATION_OFFER";
        if (from == BookingStatus.ASSIGNED && to == BookingStatus.CONFIRMED) return "PARTNER_ACCEPTED";
        if (from == BookingStatus.ASSIGNED && to == BookingStatus.PENDING)
            return reason != null && reason.toLowerCase(java.util.Locale.ROOT).contains("reject")
                    ? "PARTNER_REJECTED" : "ALLOCATION_OFFER_EXPIRED";
        if (to == BookingStatus.REJECTED) return "ADMIN_ALERT";
        if (to == BookingStatus.CANCELLED) return "BOOKING_CANCELLED";
        if (to == BookingStatus.PARTNER_ON_THE_WAY)
            return isGpsTransition(reason) ? "PARTNER_ON_THE_WAY" : null;
        if (to == BookingStatus.PARTNER_NEARBY)
            return isGpsTransition(reason) ? "PARTNER_NEARBY" : null;
        if (to == BookingStatus.PARTNER_ARRIVED) return "PARTNER_ARRIVED";
        if (to == BookingStatus.IN_PROGRESS) return "SERVICE_STARTED";
        if (to == BookingStatus.COMPLETED) return "SERVICE_COMPLETED";
        return null;
    }

    private static boolean isGpsTransition(String reason) {
        return reason != null && reason.toLowerCase(java.util.Locale.ROOT).contains("gps");
    }

    private Customer mapCustomer(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Customer(rs.getObject("id", UUID.class), rs.getString("auth_subject"), rs.getString("full_name"),
                rs.getString("email"), rs.getString("phone"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private Booking mapBooking(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Date date = rs.getDate("appointment_date");
        Timestamp start = rs.getTimestamp("appointment_start");
        return new Booking(rs.getObject("id", UUID.class), rs.getObject("customer_id", UUID.class),
                rs.getString("customer_subject"), rs.getString("customer_name"), rs.getString("customer_email"),
                rs.getString("contact_phone"), rs.getString("category"), rs.getString("required_skill"),
                date.toLocalDate(), start.toInstant(),
                rs.getTimestamp("appointment_end").toInstant(), rs.getInt("service_duration_minutes"),
                rs.getString("address"), Pg.read(rs, "loc"), rs.getString("special_instructions"),
                rs.getObject("assigned_partner_id", UUID.class), BookingStatus.valueOf(rs.getString("status")),
                rs.getLong("version"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }
}

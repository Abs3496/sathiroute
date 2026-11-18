package com.routemesh.tracking.adapter;

import com.routemesh.booking.domain.BookingStatus;
import com.routemesh.geo.domain.GeoPoint;
import com.routemesh.tracking.application.PartnerTrackingStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcPartnerTrackingStore implements PartnerTrackingStore {
    private final JdbcClient db;
    JdbcPartnerTrackingStore(JdbcClient db) { this.db = db; }

    private static final String PARTNER_COLS = "select id,auth_subject,name,email,latitude,longitude,location_updated_at,active,registration_status from service_partner ";

    @Override
    public Optional<Partner> partnerForSubject(String subject) {
        return db.sql(PARTNER_COLS + "where auth_subject=?").param(subject).query(this::partnerRow).optional();
    }

    @Override
    public Optional<Partner> partner(UUID partnerId) {
        return db.sql(PARTNER_COLS + "where id=?").param(partnerId).query(this::partnerRow).optional();
    }

    @Override
    public List<Journey> journeys(UUID partnerId) {
        return db.sql(journeySql() + " where b.assigned_partner_id=? and b.status in "
                        + "('CONFIRMED','PARTNER_ON_THE_WAY','PARTNER_NEARBY','PARTNER_ARRIVED','IN_PROGRESS') order by b.appointment_start")
                .param(partnerId).query(this::journeyRow).list();
    }

    @Override
    public List<Journey> activeJourneys() {
        return db.sql(journeySql() + " where b.status in ('CONFIRMED','PARTNER_ON_THE_WAY','PARTNER_NEARBY','PARTNER_ARRIVED','IN_PROGRESS') "
                        + "order by b.appointment_start")
                .query(this::journeyRow).list();
    }

    @Override
    @Transactional
    public void recordLocation(UUID partnerId, GeoPoint point, Instant at) {
        int updated = db.sql("update service_partner set latitude=?,longitude=?,location_updated_at=?, "
                        + "geo_location=ST_SetSRID(ST_MakePoint(?,?),4326)::geography "
                        + "where id=? and active and registration_status='APPROVED'")
                .params(point.lat(), point.lng(), Timestamp.from(at), point.lng(), point.lat(), partnerId).update();
        if (updated == 0) return;
        db.sql("insert into partner_location_history(partner_id,recorded_at,position) values(?,?,ST_SetSRID(ST_MakePoint(?,?),4326)::geography) on conflict do nothing")
                .params(partnerId, Timestamp.from(at), point.lng(), point.lat()).update();
    }

    @Override
    public List<Location> history(UUID partnerId, int limit) {
        return db.sql("select recorded_at,ST_Y(position::geometry) lat,ST_X(position::geometry) lng "
                        + "from partner_location_history where partner_id=? order by recorded_at desc limit ?")
                .params(partnerId, limit).query((rs,n) -> new Location(rs.getTimestamp(1).toInstant(),
                        new GeoPoint(rs.getDouble("lat"), rs.getDouble("lng")))).list();
    }

    @Override
    public int purgeHistoryBefore(Instant cutoff) {
        return db.sql("delete from partner_location_history where recorded_at < ?")
                .param(Timestamp.from(cutoff)).update();
    }

    @Override
    public boolean claimNotification(UUID bookingId, BookingStatus status, Instant at) {
        return db.sql("insert into service_tracking_notification(booking_id,status,notified_at) values(?,?,?) on conflict do nothing")
                .params(bookingId, status.name(), Timestamp.from(at)).update() == 1;
    }

    private String journeySql() {
        return """
                select b.id booking_id,p.id partner_id,p.name partner_name,p.auth_subject partner_subject,
                  b.customer_subject,b.customer_name,b.customer_email,b.status,b.appointment_start,b.appointment_end,b.version,
                  ST_Y(b.location::geometry) customer_lat,ST_X(b.location::geometry) customer_lng
                from service_booking b join service_partner p on p.id=b.assigned_partner_id
                """;
    }

    private Partner partnerRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        Double lat = (Double) rs.getObject("latitude");
        Double lng = (Double) rs.getObject("longitude");
        var updated = rs.getTimestamp("location_updated_at");
        return new Partner(rs.getObject("id", UUID.class), rs.getString("auth_subject"), rs.getString("name"),
                rs.getString("email"), lat == null || lng == null ? null : new GeoPoint(lat, lng),
                updated == null ? null : updated.toInstant(), rs.getBoolean("active"),
                "APPROVED".equals(rs.getString("registration_status")));
    }

    private Journey journeyRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Journey(rs.getObject("booking_id", UUID.class), rs.getObject("partner_id", UUID.class),
                rs.getString("partner_name"), rs.getString("partner_subject"), rs.getString("customer_subject"),
                rs.getString("customer_name"), rs.getString("customer_email"),
                new GeoPoint(rs.getDouble("customer_lat"), rs.getDouble("customer_lng")),
                BookingStatus.valueOf(rs.getString("status")), rs.getTimestamp("appointment_start").toInstant(),
                rs.getTimestamp("appointment_end").toInstant(), rs.getLong("version"));
    }
}

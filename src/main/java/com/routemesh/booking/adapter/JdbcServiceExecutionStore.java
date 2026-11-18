package com.routemesh.booking.adapter;

import com.routemesh.booking.application.ServiceExecutionStore;
import com.routemesh.booking.domain.BookingStatus;
import com.routemesh.outbox.application.Outbox;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcServiceExecutionStore implements ServiceExecutionStore {
    private final JdbcClient db;
    private final Outbox outbox;
    JdbcServiceExecutionStore(JdbcClient db, Outbox outbox) { this.db = db; this.outbox = outbox; }

    @Override
    public Optional<Execution> find(UUID bookingId) {
        return db.sql("select * from service_completion where booking_id=?").param(bookingId)
                .query((rs,n) -> new Execution(rs.getObject("booking_id", UUID.class), rs.getObject("partner_id", UUID.class),
                        instant(rs, "arrived_at"), instant(rs, "started_at"), instant(rs, "completion_submitted_at"),
                        instant(rs, "completed_at"), instant(rs, "customer_confirmed_at"), rs.getBoolean("otp_required"),
                        rs.getBoolean("otp_verified"), rs.getString("completion_notes"), rs.getInt("otp_attempts"),
                        rs.getLong("version"))).optional();
    }

    @Override
    @Transactional
    public void arrival(UUID bookingId, UUID partnerId, Instant now, String actor) {
        int inserted = db.sql("insert into service_completion(booking_id,partner_id,arrived_at) values(?,?,?) on conflict do nothing")
                .params(bookingId, partnerId, Timestamp.from(now)).update();
        if (inserted == 1) event(bookingId, "ARRIVED", actor, now, "partner arrival confirmed");
    }

    @Override
    @Transactional
    public void start(UUID bookingId, Instant now, String actor) {
        int changed = db.sql("update service_completion set started_at=?,version=version+1 where booking_id=? and started_at is null")
                .params(Timestamp.from(now), bookingId).update();
        if (changed == 0) {
            if (find(bookingId).isEmpty()) throw new IllegalStateException("service arrival record is missing");
            throw new IllegalStateException("service was already started");
        }
        event(bookingId, "STARTED", actor, now, "service started");
    }

    @Override
    @Transactional
    public void submitCompletion(UUID bookingId, Instant now, String notes, boolean otpRequired,
                                 String otpHash, String actor) {
        int changed = db.sql("update service_completion set completed_at=?,completion_submitted_at=?,completion_notes=?, "
                        + "otp_required=?,otp_hash=?,otp_verified=false,version=version+1 where booking_id=? and started_at is not null and completed_at is null")
                .params(Timestamp.from(now), Timestamp.from(now), notes, otpRequired, otpHash, bookingId).update();
        if (changed == 0) throw new IllegalStateException("service must be started and not already completed");
        event(bookingId, "COMPLETION_SUBMITTED", actor, now, notes == null ? "" : notes);
        outbox.append(Outbox.SERVICE_EVENTS, bookingId.toString(), "SERVICE_COMPLETION_SUBMITTED",
                Map.of("bookingId", bookingId.toString(), "status", BookingStatus.IN_PROGRESS.name()));
    }

    @Override
    @Transactional
    public OtpResult verifyCustomerOtp(UUID bookingId, String candidateHash, Instant now, String actor) {
        var row = db.sql("select otp_hash,otp_attempts,otp_required,completed_at,customer_confirmed_at "
                        + "from service_completion where booking_id=? for update")
                .param(bookingId).query((rs,n) -> new Object[]{rs.getString("otp_hash"), rs.getInt("otp_attempts"),
                        rs.getBoolean("otp_required"), instant(rs,"completed_at"), instant(rs,"customer_confirmed_at")}).optional();
        if (row.isEmpty() || !(boolean)row.get()[2] || row.get()[3] == null) return new OtpResult(false, false);
        if (row.get()[4] != null) return new OtpResult(true, false);
        int attempts = (int)row.get()[1];
        if (attempts >= 5) return new OtpResult(false, true);
        String expected = (String)row.get()[0];
        boolean correct = expected != null && java.security.MessageDigest.isEqual(
                expected.trim().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                candidateHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        if (!correct) {
            db.sql("update service_completion set otp_attempts=otp_attempts+1,version=version+1 where booking_id=?")
                    .param(bookingId).update();
            event(bookingId, "OTP_FAILED", actor, now, "attempt " + (attempts + 1));
            return new OtpResult(false, attempts + 1 >= 5);
        }
        db.sql("update service_completion set otp_verified=true,customer_confirmed_at=?,version=version+1 where booking_id=?")
                .params(Timestamp.from(now), bookingId).update();
        event(bookingId, "CUSTOMER_CONFIRMED", actor, now, "OTP verified");
        event(bookingId, "COMPLETED", actor, now, "customer confirmed service completion");
        return new OtpResult(true, false);
    }

    @Override
    @Transactional
    public boolean confirmWithoutOtp(UUID bookingId, Instant now, String actor) {
        int changed = db.sql("update service_completion set customer_confirmed_at=?,version=version+1 "
                        + "where booking_id=? and completed_at is not null and otp_required=false and customer_confirmed_at is null")
                .params(Timestamp.from(now), bookingId).update();
        if (changed == 1) {
            event(bookingId, "CUSTOMER_CONFIRMED", actor, now, "customer confirmed service completion");
            event(bookingId, "COMPLETED", actor, now, "service booking closed");
        }
        return changed == 1 || find(bookingId).map(e -> !e.otpRequired() && e.customerConfirmedAt() != null).orElse(false);
    }

    @Override
    @Transactional
    public void addEvidence(EvidenceContent evidence) {
        var e = evidence.evidence();
        int inserted = db.sql("insert into service_completion_evidence(id,booking_id,uploaded_by,file_name,content_type,sha256,content,uploaded_at) "
                        + "select ?,?,?, ?,?,?,?,? where exists(select 1 from service_completion where booking_id=? and started_at is not null and completed_at is null)")
                .params(e.id(), e.bookingId(), e.uploadedBy(), e.fileName(), e.contentType(), e.sha256(), evidence.content(),
                        Timestamp.from(e.uploadedAt()), e.bookingId()).update();
        if (inserted != 1) throw new IllegalStateException("evidence can only be stored during active service");
        event(e.bookingId(), "EVIDENCE_ADDED", e.uploadedBy(), e.uploadedAt(), e.sha256());
    }

    @Override
    public List<Evidence> evidence(UUID bookingId) {
        return db.sql("select id,booking_id,uploaded_by,file_name,content_type,sha256,octet_length(content) bytes,uploaded_at "
                        + "from service_completion_evidence where booking_id=? order by uploaded_at")
                .param(bookingId).query((rs,n) -> new Evidence(rs.getObject("id",UUID.class),
                        rs.getObject("booking_id",UUID.class),rs.getString("uploaded_by"),rs.getString("file_name"),
                        rs.getString("content_type"),rs.getString("sha256"),rs.getInt("bytes"),rs.getTimestamp("uploaded_at").toInstant())).list();
    }

    @Override
    public Optional<EvidenceContent> evidence(UUID bookingId, UUID evidenceId) {
        return db.sql("select id,booking_id,uploaded_by,file_name,content_type,sha256,octet_length(content) bytes,uploaded_at,content "
                        + "from service_completion_evidence where booking_id=? and id=?")
                .params(bookingId,evidenceId).query((rs,n) -> new EvidenceContent(new Evidence(rs.getObject("id",UUID.class),
                        rs.getObject("booking_id",UUID.class),rs.getString("uploaded_by"),rs.getString("file_name"),
                        rs.getString("content_type"),rs.getString("sha256"),rs.getInt("bytes"),rs.getTimestamp("uploaded_at").toInstant()),
                        rs.getBytes("content"))).optional();
    }

    @Override
    public List<Event> events(UUID bookingId) {
        return db.sql("select id,event_type,actor_subject,occurred_at,details from service_completion_event where booking_id=? order by id")
                .param(bookingId).query((rs,n) -> new Event(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getTimestamp(4).toInstant(),rs.getString(5))).list();
    }

    @Override
    public List<Monitor> monitor(String status) {
        String sql = """
                select b.id booking_id,p.id partner_id,p.name partner_name,b.customer_name,b.status,b.appointment_start,
                       c.arrived_at,c.started_at,c.completed_at,c.customer_confirmed_at,c.otp_required,c.otp_verified
                from service_booking b join service_partner p on p.id=b.assigned_partner_id
                  left join service_completion c on c.booking_id=b.id
                where b.status in ('ASSIGNED','CONFIRMED','PARTNER_ON_THE_WAY','PARTNER_NEARBY','PARTNER_ARRIVED','IN_PROGRESS','COMPLETED')
                  and (? is null or b.status=?) order by b.appointment_start desc limit 500
                """;
        return db.sql(sql).params(status, status).query((rs,n) -> new Monitor(rs.getObject("booking_id",UUID.class),
                rs.getObject("partner_id",UUID.class),rs.getString("partner_name"),rs.getString("customer_name"),
                BookingStatus.valueOf(rs.getString("status")),rs.getTimestamp("appointment_start").toInstant(),
                instant(rs,"arrived_at"),instant(rs,"started_at"),instant(rs,"completed_at"),instant(rs,"customer_confirmed_at"),
                rs.getBoolean("otp_required"),rs.getBoolean("otp_verified"))).list();
    }

    private void event(UUID bookingId,String type,String actor,Instant at,String details) {
        db.sql("insert into service_completion_event(booking_id,event_type,actor_subject,occurred_at,details) values(?,?,?,?,?)")
                .params(bookingId,type,actor,Timestamp.from(at),details == null ? "" : details).update();
    }

    private static Instant instant(java.sql.ResultSet rs,String col) throws java.sql.SQLException {
        var ts=rs.getTimestamp(col); return ts == null ? null : ts.toInstant();
    }
}

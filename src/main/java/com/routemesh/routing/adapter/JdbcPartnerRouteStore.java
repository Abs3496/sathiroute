package com.routemesh.routing.adapter;

import com.routemesh.geo.domain.GeoPoint;
import com.routemesh.routing.application.PartnerRouteStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcPartnerRouteStore implements PartnerRouteStore {
    private final JdbcClient db;

    JdbcPartnerRouteStore(JdbcClient db) { this.db = db; }

    @Override
    public Optional<Day> day(UUID partnerId, LocalDate date) {
        return db.sql("""
                select p.id, ST_Y(p.geo_location::geometry) lat, ST_X(p.geo_location::geometry) lng,
                       a.shift_start, a.shift_end
                from service_partner p cross join lateral (
                  select min(starts_at) shift_start, max(ends_at) shift_end from partner_availability
                  where partner_id=p.id and active and day_of_week=extract(isodow from ?::date)::smallint
                ) a
                where p.id=? and p.active and p.registration_status='APPROVED' and p.geo_location is not null
                  and a.shift_start is not null
                  and not exists(select 1 from partner_time_off t where t.partner_id=p.id and t.unavailable_date=?)
                """).params(Date.valueOf(date), partnerId, Date.valueOf(date)).query((rs,n) ->
                new Day(rs.getObject("id", UUID.class), new GeoPoint(rs.getDouble("lat"), rs.getDouble("lng")),
                        rs.getTime("shift_start").toLocalTime(), rs.getTime("shift_end").toLocalTime())).optional();
    }

    @Override
    public List<Visit> visits(UUID partnerId, LocalDate date) {
        return db.sql("""
                select b.id, b.appointment_start, b.appointment_end, b.service_duration_minutes, b.status,
                       ST_Y(b.location::geometry) lat, ST_X(b.location::geometry) lng
                from service_booking b where b.assigned_partner_id=? and b.appointment_date=?
                  and b.status in ('ASSIGNED','CONFIRMED','PARTNER_ON_THE_WAY','PARTNER_NEARBY','PARTNER_ARRIVED','IN_PROGRESS')
                order by b.appointment_start,b.id
                """).params(partnerId, Date.valueOf(date)).query((rs,n) -> new Visit(
                rs.getObject("id", UUID.class), new GeoPoint(rs.getDouble("lat"), rs.getDouble("lng")),
                rs.getTimestamp("appointment_start").toInstant(), rs.getTimestamp("appointment_end").toInstant(),
                rs.getInt("service_duration_minutes"), rs.getString("status"))).list();
    }

    @Override
    public boolean hasCompletedVisits(UUID partnerId, LocalDate date) {
        return db.sql("select exists(select 1 from service_booking where assigned_partner_id=? and appointment_date=? and status='COMPLETED')")
                .params(partnerId, Date.valueOf(date)).query(Boolean.class).single();
    }

    @Override
    public Optional<Route> route(UUID partnerId, LocalDate date) {
        var header = db.sql("select id,partner_id,service_date,status,km,planned_return,version from partner_route "
                        + "where partner_id=? and service_date=?")
                .params(partnerId, Date.valueOf(date)).query((rs,n) -> new Object[]{rs.getObject("id", UUID.class),
                        rs.getObject("partner_id", UUID.class), rs.getDate("service_date").toLocalDate(), rs.getString("status"),
                        rs.getDouble("km"), rs.getTimestamp("planned_return") == null ? null : rs.getTimestamp("planned_return").toInstant(),
                        rs.getLong("version")}).optional();
        if (header.isEmpty()) return Optional.empty();
        UUID id = (UUID) header.get()[0];
        List<Stop> stops = db.sql("select sequence,booking_id,planned_arrival from partner_route_stop where route_id=? order by sequence")
                .param(id).query((rs,n) -> new Stop(rs.getInt(1), rs.getObject(2, UUID.class), rs.getTimestamp(3).toInstant())).list();
        return Optional.of(new Route(id, (UUID) header.get()[1], (LocalDate) header.get()[2], (String) header.get()[3],
                (double) header.get()[4], (Instant) header.get()[5], (long) header.get()[6], stops));
    }

    @Override
    public Optional<Route> routeForId(UUID routeId) {
        return db.sql("select partner_id,service_date from partner_route where id=?").param(routeId)
                .query((rs,n) -> new Object[]{rs.getObject(1, UUID.class), rs.getDate(2).toLocalDate()})
                .optional().flatMap(key -> route((UUID) key[0], (LocalDate) key[1]));
    }

    @Override
    public List<DayKey> daysToReconcile(LocalDate from, LocalDate to) {
        return db.sql("""
                select distinct partner_id, service_date from (
                  select assigned_partner_id partner_id, appointment_date service_date from service_booking
                    where assigned_partner_id is not null and appointment_date between ? and ?
                      and status in ('ASSIGNED','CONFIRMED','PARTNER_ON_THE_WAY','PARTNER_NEARBY','PARTNER_ARRIVED','IN_PROGRESS')
                  union
                  select partner_id, service_date from partner_route where service_date between ? and ?
                    and status in ('PLANNED','ACTIVE')
                ) d order by service_date,partner_id
                """).params(Date.valueOf(from), Date.valueOf(to), Date.valueOf(from), Date.valueOf(to))
                .query((rs,n) -> new DayKey(rs.getObject(1, UUID.class), rs.getDate(2).toLocalDate())).list();
    }

    @Override
    public Optional<UUID> partnerForSubject(String subject) {
        return db.sql("select id from service_partner where auth_subject=?").param(subject).query(UUID.class).optional();
    }

    @Override
    public List<History> history(UUID routeId) {
        return db.sql("select version,status,changed_at,changed_by,reason,snapshot::text from partner_route_history where route_id=? order by id")
                .param(routeId).query((rs,n) -> new History(rs.getLong(1), rs.getString(2), rs.getTimestamp(3).toInstant(),
                        rs.getString(4), rs.getString(5), rs.getString(6))).list();
    }

    @Override
    @Transactional
    public Route save(UUID partnerId, LocalDate date, String status, double km, Instant plannedReturn,
                      List<Stop> stops, Instant now, String actor, String reason) {
        var existing = route(partnerId, date);
        UUID routeId = existing.map(Route::id).orElseGet(UUID::randomUUID);
        if (existing.isEmpty()) {
            db.sql("insert into partner_route(id,partner_id,service_date,status,created_at,updated_at) values(?,?,?,?,?,?)")
                    .params(routeId, partnerId, Date.valueOf(date), status, Timestamp.from(now), Timestamp.from(now)).update();
        } else {
            db.sql("select id from partner_route where id=? for update").param(routeId).query(UUID.class).single();
            db.sql("update partner_route set status=?,version=version+1,updated_at=? where id=?")
                    .params(status, Timestamp.from(now), routeId).update();
        }
        db.sql("delete from partner_route_stop where route_id=?").param(routeId).update();
        for (int i=0; i<stops.size(); i++) {
            Stop stop = stops.get(i);
            db.sql("delete from partner_route_stop where booking_id=?").param(stop.bookingId()).update();
            db.sql("insert into partner_route_stop(route_id,sequence,booking_id,planned_arrival) values(?,?,?,?)")
                    .params(routeId, i+1, stop.bookingId(), Timestamp.from(stop.plannedArrival())).update();
        }
        db.sql("update partner_route set km=?,planned_return=?,updated_at=? where id=?")
                .params(km, plannedReturn == null ? null : Timestamp.from(plannedReturn), Timestamp.from(now), routeId).update();
        long version = db.sql("select version from partner_route where id=?").param(routeId).query(Long.class).single();
        db.sql("""
                insert into partner_route_history(route_id,status,version,changed_at,changed_by,reason,snapshot)
                select r.id,r.status,r.version,?,?,?,jsonb_build_object(
                  'distanceKm',r.km,'plannedReturn',r.planned_return,
                  'stops',coalesce(jsonb_agg(jsonb_build_object('sequence',s.sequence,'bookingId',s.booking_id,
                    'plannedArrival',s.planned_arrival) order by s.sequence) filter(where s.booking_id is not null),'[]'::jsonb))
                from partner_route r left join partner_route_stop s on s.route_id=r.id where r.id=? group by r.id
                """).params(Timestamp.from(now), actor, reason, routeId).update();
        return route(partnerId, date).orElseThrow();
    }
}

package com.routemesh.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.routemesh.support.Api;
import com.routemesh.support.ItBase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises service allocation, route creation, location read access, completion, and notification delivery. */
class ServiceBookingFlowIT extends ItBase {

    @Test
    void customerBookingIsAllocatedRoutedTrackedAndClosedWithCustomerConfirmation() {
        Hub centre = hub(0, 0, 500);
        String partnerSubject = unique("partner");
        Api partner = asRole(partnerSubject, "SERVICE_PARTNER");
        JsonNode profile = partner.post("/v1/partners", Map.of(
                "centreId", centre.id(), "name", "Nurse Test", "email", partnerSubject,
                "phone", "+910000000001", "qualificationDetails", "Registered nursing qualification",
                "serviceRadiusKm", 10.0,
                "skills", List.of(Map.of("category", "NURSING", "code", "RN", "qualificationDetails", "RN license"))))
                .expect(201).body();
        String partnerId = profile.path("id").asText();
        dispatcher().patch("/v1/admin/partners/" + partnerId + "/verification", Map.of("approved", true), Map.of())
                .expect(200);
        dispatcher().patch("/v1/admin/partners/" + partnerId + "/status", Map.of("active", true), Map.of())
                .expect(200);

        partner.patch("/v1/partners/me/location", Map.of("latitude", centre.at().lat(), "longitude", centre.at().lng()), Map.of())
                .expect(200);
        partner.put("/v1/partners/me/availability", Map.of("windows", List.of(Map.of(
                "dayOfWeek", tomorrow().getDayOfWeek().getValue(), "startsAt", "08:00", "endsAt", "20:00"))))
                .expect(200);
        partner.patch("/v1/partners/me/availability/status", Map.of("available", true), Map.of()).expect(200);

        String customerSubject = unique("customer");
        Api customer = asRole(customerSubject, "CUSTOMER");
        customer.post("/v1/customers", Map.of("name", "Customer Test", "email", customerSubject,
                "phone", "+910000000002")).expect(201);
        Map<String, Object> bookingBody = Map.of(
                "serviceCategory", "NURSING", "requiredSkill", "RN", "appointmentDate", tomorrow().toString(),
                "appointmentStart", "10:00", "serviceDurationMinutes", 60,
                "address", "Test care address", "latitude", centre.at().off(0.2, 0).lat(),
                "longitude", centre.at().off(0.2, 0).lng(), "notes", "Integration test booking");
        String idemKey = UUID.randomUUID().toString();
        JsonNode created = customer.post("/v1/bookings", bookingBody, key(idemKey)).expect(201).body();
        String bookingId = created.path("id").asText();
        assertThat(created.path("status").asText()).isEqualTo("NEW");
        assertThat(customer.post("/v1/bookings", bookingBody, key(idemKey)).expect(200)
                .header("Idempotent-Replayed")).isEqualTo("true");
        asRole(unique("other-customer"), "CUSTOMER").get("/v1/bookings/" + bookingId).expect(404);
        partner.get("/v1/bookings/" + bookingId).expect(403);

        eventually(() -> "ASSIGNED".equals(db.sql("select status from service_booking where id=?::uuid")
                .param(bookingId).query(String.class).single()));
        assertThat(count("select count(*) from allocation_attempt where booking_id=?::uuid and status='OFFERED'",
                bookingId)).isEqualTo(1);
        eventually(() -> mailsTo(partnerSubject).stream()
                .anyMatch(m -> m.path("Subject").asText().contains("service offer")));
        long offeredVersion = db.sql("select version from service_booking where id=?::uuid").param(bookingId)
                .query(Long.class).single();
        partner.post("/v1/partners/me/bookings/" + bookingId + "/accept",
                Map.of("expectedVersion", offeredVersion)).expect(200);
        eventually(() -> "CONFIRMED".equals(db.sql("select status from service_booking where id=?::uuid")
                .param(bookingId).query(String.class).single()));

        var route = dispatcher().post("/v1/admin/partners/" + partnerId + "/routes/" + tomorrow() + "/recalculate", Map.of())
                .expect(200).body();
        assertThat(route.path("stops")).hasSize(1);
        assertThat(route.path("stops").get(0).path("bookingId").asText()).isEqualTo(bookingId);

        partner.patch("/v1/partners/me/location", Map.of("latitude", centre.at().off(0.2, 0).lat(),
                "longitude", centre.at().off(0.2, 0).lng()), Map.of()).expect(200);
        JsonNode location = partner.post("/v1/partners/me/location:pings", Map.of("fixes", List.of(
                Map.of("latitude", centre.at().off(0.2, 0).lat(), "longitude", centre.at().off(0.2, 0).lng(),
                        "at", Instant.now().plusSeconds(1).toString())))).expect(200).body();
        assertThat(location.path("queued").asInt()).isEqualTo(1);

        long confirmedVersion = db.sql("select version from service_booking where id=?::uuid").param(bookingId)
                .query(Long.class).single();
        partner.post("/v1/partners/me/bookings/" + bookingId + "/arrive",
                Map.of("expectedVersion", confirmedVersion)).expect(200);
        assertThat(customer.get("/v1/bookings/" + bookingId + "/tracking").expect(200)
                .body().path("status").asText()).isEqualTo("PARTNER_ARRIVED");

        long arrivedVersion = db.sql("select version from service_booking where id=?::uuid").param(bookingId)
                .query(Long.class).single();
        partner.post("/v1/partners/me/bookings/" + bookingId + "/start",
                Map.of("expectedVersion", arrivedVersion)).expect(200);
        long startedVersion = db.sql("select version from service_booking where id=?::uuid").param(bookingId)
                .query(Long.class).single();
        partner.post("/v1/partners/me/bookings/" + bookingId + "/complete",
                Map.of("expectedVersion", startedVersion, "completionNotes", "Visit completed"))
                .expect(200);
        assertThat(db.sql("select status from service_booking where id=?::uuid").param(bookingId)
                .query(String.class).single()).isEqualTo("IN_PROGRESS");
        customer.post("/v1/bookings/" + bookingId + "/completion-confirmation", Map.of("confirmed", true))
                .expect(200);
        assertThat(db.sql("select status from service_booking where id=?::uuid").param(bookingId)
                .query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(db.sql("select count(*) from service_completion_event where booking_id=?::uuid and event_type in "
                + "('ARRIVED','STARTED','COMPLETION_SUBMITTED','CUSTOMER_CONFIRMED','COMPLETED')")
                .param(bookingId).query(Long.class).single()).isEqualTo(5);
        assertThat(dispatcher().get("/v1/admin/service-executions?status=COMPLETED").expect(200).body().toString())
                .contains(bookingId);
        eventually(() -> mailsTo(customerSubject).stream()
                .anyMatch(m -> mailText(m).contains("Service completion is confirmed")));
    }
}

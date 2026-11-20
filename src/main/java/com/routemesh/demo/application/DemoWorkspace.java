package com.routemesh.demo.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ephemeral showcase data. This store is deliberately separate from production persistence. */
@Service
@ConditionalOnProperty(name = "routemesh.demo-api.enabled", havingValue = "true")
public class DemoWorkspace {
    private final List<Map<String, Object>> bookings = new ArrayList<>();
    private final List<Map<String, Object>> partners = new ArrayList<>();
    private final List<Map<String, Object>> journeys = new ArrayList<>();
    private final List<Map<String, Object>> shipments = new ArrayList<>();
    private final List<Map<String, Object>> services = new ArrayList<>();
    private final List<Map<String, Object>> activity = new ArrayList<>();
    private final LocalDate today = LocalDate.now();

    public DemoWorkspace() { reset(); }

    public synchronized Map<String, Object> snapshot() {
        long monthRevenue = bookings.stream().filter(b -> "COMPLETED".equals(b.get("status"))).count() * 1800L;
        long total = bookings.size();
        long complete = bookings.stream().filter(b -> "COMPLETED".equals(b.get("status"))).count();
        long success = total == 0 ? 0 : Math.round(100.0 * complete / total);
        return Map.ofEntries(Map.entry("mode", "DEMO"), Map.entry("updatedAt", LocalDateTime.now().toString()),
                Map.entry("bookings", copy(bookings)), Map.entry("partners", copy(partners)), Map.entry("journeys", copy(journeys)),
                Map.entry("shipments", copy(shipments)), Map.entry("services", copy(services)), Map.entry("activity", copy(activity)),
                Map.entry("routes", routes()), Map.entry("metrics", Map.of("revenueThisMonth", monthRevenue,
                        "successRate", success, "deliveriesToday", shipments.size())), Map.entry("reports", report()));
    }

    public synchronized Map<String, Object> createBooking(Map<String, Object> input) {
        String requestedCategory = String.valueOf(input.getOrDefault("category", input.getOrDefault("serviceCategory", "NURSING")));
        Map<String, Object> categoryService = services.stream().filter(s -> requestedCategory.equals(s.get("id"))
                || requestedCategory.equals(s.get("category")) && requestedCategory.equals(s.get("serviceCode"))).findFirst().orElse(null);
        if (categoryService != null && Boolean.FALSE.equals(categoryService.get("active"))) {
            throw new IllegalArgumentException("This service category is paused");
        }
        String requestedSkill = String.valueOf(input.getOrDefault("skill", input.getOrDefault("requiredSkill", ""))).trim();
        boolean pausedOffering = services.stream().anyMatch(s -> requestedCategory.equals(s.get("category"))
                && Boolean.FALSE.equals(s.get("active"))
                && (requestedSkill.equalsIgnoreCase(String.valueOf(s.get("name")))
                || requestedSkill.equalsIgnoreCase(String.valueOf(s.get("serviceCode")))));
        if (pausedOffering) throw new IllegalArgumentException("This service offering is paused");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "DEMO-BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        row.put("customerName", input.getOrDefault("name", input.getOrDefault("customerName", "Demo Customer")));
        row.put("customerEmail", input.getOrDefault("email", "demo@example.test"));
        row.put("customerPhone", input.getOrDefault("phone", ""));
        row.put("serviceCategory", input.getOrDefault("category", input.getOrDefault("serviceCategory", "NURSING")));
        row.put("requiredSkill", input.getOrDefault("skill", input.getOrDefault("requiredSkill", "Home care")));
        row.put("appointmentDate", input.getOrDefault("date", today.toString()));
        row.put("appointmentStart", input.getOrDefault("time", "16:00"));
        row.put("serviceDurationMinutes", Integer.parseInt(String.valueOf(input.getOrDefault("duration", "60"))));
        row.put("address", input.getOrDefault("address", "Demo service address"));
        row.put("status", "NEW"); row.put("version", 0); row.put("createdAt", LocalDateTime.now().toString());
        bookings.add(0, row); addActivity("Booking created", String.valueOf(row.get("customerName")), "Demo booking");
        return Map.copyOf(row);
    }

    public synchronized Map<String, Object> updateBooking(String id, String status) {
        Map<String, Object> row = find(bookings, id);
        row.put("status", status); row.put("version", ((Number) row.getOrDefault("version", 0)).intValue() + 1);
        row.put("updatedAt", LocalDateTime.now().toString());
        addActivity("Booking " + status.toLowerCase().replace('_', ' '), String.valueOf(row.get("customerName")), String.valueOf(row.get("serviceCategory")));
        return Map.copyOf(row);
    }

    public synchronized Map<String, Object> createPartner(Map<String, Object> input) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "DEMO-PT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        row.put("name", input.getOrDefault("name", "Demo Care Partner"));
        row.put("code", input.getOrDefault("code", row.get("id")));
        row.put("registrationStatus", "PENDING"); row.put("active", false); row.put("available", false);
        row.put("skills", List.of(Map.of("category", input.getOrDefault("category", "NURSING"), "code", input.getOrDefault("skill", "Home care"), "verified", false)));
        row.put("completedBookings", 0); row.put("upcomingBookings", 0); partners.add(0, row);
        addActivity("New partner submitted", String.valueOf(row.get("name")), "Needs review"); return Map.copyOf(row);
    }

    public synchronized Map<String, Object> updatePartner(String id, Map<String, Object> input) {
        Map<String, Object> row = find(partners, id);
        if (input.containsKey("approved")) row.put("registrationStatus", Boolean.TRUE.equals(input.get("approved")) ? "APPROVED" : "REJECTED");
        if (input.containsKey("active")) row.put("active", input.get("active"));
        if (Boolean.TRUE.equals(input.get("active"))) row.put("available", true);
        if (input.containsKey("available")) row.put("available", input.get("available"));
        addActivity("Partner updated", String.valueOf(row.get("name")), String.valueOf(row.get("registrationStatus")));
        return Map.copyOf(row);
    }

    public synchronized Map<String, Object> createShipment(Map<String, Object> input) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "DEMO-D-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        row.put("reference", input.getOrDefault("reference", row.get("id")));
        row.put("recipientName", input.getOrDefault("recipientName", "Demo Recipient"));
        row.put("address", input.getOrDefault("address", "Demo destination"));
        row.put("serviceDate", input.getOrDefault("serviceDate", today.toString()));
        row.put("status", "PLANNED"); row.put("kind", input.getOrDefault("kind", "DELIVERY"));
        row.put("routeName", input.getOrDefault("routeName", "Demo route")); row.put("version", 0);
        shipments.add(0, row); addActivity("Shipment created", String.valueOf(row.get("reference")), String.valueOf(row.get("address")));
        return Map.copyOf(row);
    }

    public synchronized Map<String, Object> updateShipment(String id, String status) {
        Map<String, Object> row = find(shipments, id); row.put("status", status);
        row.put("version", ((Number) row.getOrDefault("version", 0)).intValue() + 1);
        addActivity("Shipment " + status.toLowerCase().replace('_', ' '), String.valueOf(row.get("reference")), String.valueOf(row.get("address")));
        return Map.copyOf(row);
    }

    public synchronized Map<String, Object> createService(Map<String, Object> input) {
        String name = String.valueOf(input.getOrDefault("name", "")).trim();
        String category = String.valueOf(input.getOrDefault("category", "NURSING")).trim();
        if (name.isBlank()) throw new IllegalArgumentException("Service name is required");
        if (!List.of("NURSING", "AYURVEDIC_THERAPY", "COUNSELLING").contains(category))
            throw new IllegalArgumentException("Unknown service category");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", "DEMO-SVC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        row.put("name", name); row.put("category", category); row.put("active", true);
        row.put("description", input.getOrDefault("description", "")); row.put("createdAt", LocalDateTime.now().toString());
        services.add(row); addActivity("Service offering created", name, category);
        return Map.copyOf(row);
    }

    public synchronized Map<String, Object> updateService(String id, Map<String, Object> input) {
        Map<String, Object> service = find(services, id);
        if (input.containsKey("active")) service.put("active", Boolean.TRUE.equals(input.get("active")));
        if (input.containsKey("name")) {
            String name = String.valueOf(input.get("name")).trim();
            if (name.isBlank() || name.length() > 120) throw new IllegalArgumentException("Service name must be 1..120 characters");
            service.put("name", name);
        }
        if (input.containsKey("description")) {
            String description = String.valueOf(input.get("description")).trim();
            if (description.length() > 1000) throw new IllegalArgumentException("Service description is too long");
            service.put("description", description);
        }
        service.put("updatedAt", LocalDateTime.now().toString());
        addActivity("Service updated", String.valueOf(service.get("name")), String.valueOf(service.getOrDefault("category", id)));
        return Map.copyOf(service);
    }

    /** Compatibility for callers that only toggle the active flag. */
    public synchronized Map<String, Object> updateService(String id, boolean active) {
        return updateService(id, Map.of("active", active));
    }

    public synchronized List<Map<String, Object>> report() {
        long completed = bookings.stream().filter(b -> "COMPLETED".equals(b.get("status"))).count();
        long active = shipments.stream().filter(s -> List.of("IN_TRANSIT", "OUT_FOR_DELIVERY").contains(s.get("status"))).count();
        return List.of(Map.of("label", "Bookings", "value", bookings.size()),
                Map.of("label", "Completed visits", "value", completed),
                Map.of("label", "Deliveries in progress", "value", active),
                Map.of("label", "Active partners", "value", partners.stream().filter(p -> Boolean.TRUE.equals(p.get("active"))).count()));
    }

    private List<Map<String, Object>> routes() {
        List<Map<String, Object>> stops = shipments.stream().filter(s -> !"DELIVERED".equals(s.get("status")))
                .map(s -> Map.<String, Object>of("id", s.get("id"), "customerName", s.get("recipientName"),
                        "category", "DELIVERY", "skill", s.get("address"), "time", "10:00 AM", "duration", 30)).toList();
        return stops.isEmpty() ? List.of() : List.of(Map.of("partner", "Demo Driver One", "initials", "D1",
                "skills", "Demo deliveries", "km", 8.4, "visits", stops, "tint", "blue"));
    }

    public synchronized void reset() {
        bookings.clear(); partners.clear(); journeys.clear(); shipments.clear(); services.clear(); activity.clear();
        bookings.add(booking("DEMO-BK-1048", "Riya Sharma", "NURSING", "Physiotherapy", "CONFIRMED", "16:00", 120, "Gomti Nagar, Lucknow"));
        bookings.add(booking("DEMO-BK-1049", "Amit Kumar", "COUNSELLING", "Counselling", "ASSIGNED", "17:30", 60, "Hazratganj, Lucknow"));
        bookings.add(booking("DEMO-BK-1050", "Priya Patel", "AYURVEDIC_THERAPY", "Wellness therapy", "NEW", "18:00", 60, "Aliganj, Lucknow"));
        bookings.get(0).put("partnerName", "Demo Partner Asha"); bookings.get(0).put("assignedPartnerId", "DEMO-PT-01");
        bookings.get(1).put("partnerName", "Demo Partner Vikram"); bookings.get(1).put("assignedPartnerId", "DEMO-PT-02");
        Map<String, Object> history = booking("DEMO-BK-1047", "Demo Customer Kabir", "AYURVEDIC_THERAPY", "Recovery therapy", "COMPLETED", "11:00", 60, "Gomti Nagar, Lucknow");
        history.put("appointmentDate", today.minusDays(1).toString()); bookings.add(history);
        partners.add(partner("DEMO-PT-01", "Demo Partner Asha", true, true, "APPROVED", "NURSING"));
        partners.add(partner("DEMO-PT-02", "Demo Partner Vikram", true, false, "APPROVED", "AYURVEDIC_THERAPY"));
        partners.add(partner("DEMO-PT-03", "Demo Partner Neha", false, false, "PENDING", "COUNSELLING"));
        journeys.add(Map.of("bookingId", "DEMO-BK-1048", "partnerId", "DEMO-PT-01", "partnerName", "Demo Partner Asha", "customerName", "Riya Sharma", "status", "PARTNER_ON_THE_WAY", "distanceKm", 3.2, "eta", LocalDateTime.now().plusMinutes(18).toString()));
        shipments.add(shipment("DEMO-D-2026-1124", "Lucknow to Gomti Nagar", "IN_TRANSIT", "Demo Recipient A"));
        shipments.add(shipment("DEMO-D-2026-1123", "Lucknow to Hazratganj", "OUT_FOR_DELIVERY", "Demo Recipient B"));
        shipments.add(shipment("DEMO-D-2026-1122", "Lucknow to Aliganj", "DELIVERED", "Demo Recipient C"));
        services.add(service("NURSING", "Nursing", true)); services.add(service("AYURVEDIC_THERAPY", "Ayurvedic Therapy", true)); services.add(service("COUNSELLING", "Counselling", true));
        addActivity("Booking completed", "Demo customer", "Ayurvedic Therapy");
        addActivity("Shipment dispatched", "DEMO-D-2026-1124", "Lucknow demo route");
        addActivity("Partner approved", "Demo Partner Asha", "Nursing");
    }

    private Map<String, Object> booking(String id, String name, String category, String skill, String status, String time, int duration, String address) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id); row.put("customerName", name); row.put("customerEmail", "demo@example.test"); row.put("serviceCategory", category);
        row.put("requiredSkill", skill); row.put("appointmentDate", today.toString()); row.put("appointmentStart", time);
        row.put("serviceDurationMinutes", duration); row.put("address", address); row.put("status", status); row.put("version", 0);
        row.put("createdAt", LocalDateTime.now().minusMinutes(12).toString()); return row;
    }
    private Map<String, Object> partner(String id, String name, boolean active, boolean available, String status, String category) {
        return new LinkedHashMap<>(Map.of("id", id, "name", name, "code", id, "active", active, "available", available,
                "registrationStatus", status, "skills", List.of(Map.of("category", category, "code", "Demo skill", "verified", "APPROVED".equals(status))),
                "completedBookings", 12, "upcomingBookings", 2));
    }
    private Map<String, Object> shipment(String id, String route, String status, String recipient) {
        return new LinkedHashMap<>(Map.of("id", id, "reference", id, "routeName", route, "address", route.replace("Lucknow to ", ""),
                "recipientName", recipient, "serviceDate", today.toString(), "kind", "DELIVERY", "status", status, "version", 0));
    }
    private Map<String, Object> service(String id, String name, boolean active) { return new LinkedHashMap<>(Map.of("id", id, "name", name, "active", active)); }
    private void addActivity(String title, String subject, String detail) {
        activity.add(0, Map.of("id", UUID.randomUUID().toString(), "title", title, "subject", subject, "detail", detail, "at", LocalDateTime.now().toString()));
        if (activity.size() > 12) activity.remove(activity.size() - 1);
    }
    private static Map<String, Object> find(List<Map<String, Object>> rows, String id) {
        return rows.stream().filter(row -> id.equals(String.valueOf(row.get("id")))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Demo record not found: " + id));
    }
    private static List<Map<String, Object>> copy(List<Map<String, Object>> rows) { return rows.stream().map(Map::copyOf).toList(); }
}

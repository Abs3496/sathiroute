package com.routemesh.demo.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DemoWorkspaceTest {
    @Test
    void resetProvidesACompleteShowcaseDataset() {
        var workspace = new DemoWorkspace();
        Map<String, Object> view = workspace.snapshot();

        assertThat(view.get("mode")).isEqualTo("DEMO");
        assertThat(rows(view, "bookings")).hasSize(4);
        assertThat(rows(view, "partners")).hasSize(3);
        assertThat(rows(view, "shipments")).hasSize(3);
        assertThat(rows(view, "services")).hasSize(3);
        assertThat(rows(view, "journeys")).hasSize(1);
        assertThat(rows(view, "routes")).hasSize(1);
    }

    @Test
    void dashboardActionsUpdateSharedDemoRecordsAndResetRestoresThem() {
        var workspace = new DemoWorkspace();
        Map<String, Object> booking = workspace.createBooking(Map.of("name", "Showcase Customer", "category", "NURSING"));
        workspace.updateBooking(String.valueOf(booking.get("id")), "ASSIGNED");
        Map<String, Object> partner = workspace.createPartner(Map.of("name", "Showcase Partner"));
        workspace.updatePartner(String.valueOf(partner.get("id")), Map.of("approved", true));
        Map<String, Object> shipment = workspace.createShipment(Map.of("recipientName", "Showcase Recipient", "address", "Demo Street"));
        workspace.updateShipment(String.valueOf(shipment.get("id")), "OUT_FOR_DELIVERY");
        workspace.updateService("NURSING", false);

        Map<String, Object> changed = workspace.snapshot();
        assertThat(rows(changed, "bookings")).anyMatch(row -> "ASSIGNED".equals(row.get("status")));
        assertThat(rows(changed, "partners")).anyMatch(row -> "APPROVED".equals(row.get("registrationStatus")));
        assertThat(rows(changed, "shipments")).anyMatch(row -> "OUT_FOR_DELIVERY".equals(row.get("status")));
        assertThat(rows(changed, "services")).anyMatch(row -> "NURSING".equals(row.get("id")) && Boolean.FALSE.equals(row.get("active")));
        assertThat(rows(changed, "activity")).hasSizeGreaterThan(3);

        workspace.reset();
        assertThat(rows(workspace.snapshot(), "bookings")).hasSize(4);
        assertThat(rows(workspace.snapshot(), "shipments")).hasSize(3);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> workspace, String key) {
        return (List<Map<String, Object>>) workspace.get(key);
    }
}

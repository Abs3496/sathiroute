package com.routemesh.tracking.adapter;

import com.routemesh.shared.security.Caller;
import com.routemesh.tracking.application.PartnerTrackingService;
import com.routemesh.tracking.application.PartnerTrackingStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
class PartnerTrackingController {
    record GpsBatch(List<PartnerTrackingService.GpsFix> fixes) { }

    private final PartnerTrackingService tracking;
    PartnerTrackingController(PartnerTrackingService tracking) { this.tracking = tracking; }

    @PostMapping("/v1/partners/me/location:pings")
    PartnerTrackingService.Ingested ingest(Caller caller, @RequestBody GpsBatch body) {
        return tracking.ingest(caller, body == null ? null : body.fixes());
    }

    @GetMapping("/v1/bookings/{bookingId}/tracking")
    PartnerTrackingService.CustomerTracking customer(Caller caller, @PathVariable UUID bookingId) {
        return tracking.customer(caller, bookingId);
    }

    @GetMapping("/v1/admin/tracking/partners")
    List<PartnerTrackingService.AdminJourney> dashboard(Caller caller) {
        return tracking.adminDashboard(caller);
    }

    @GetMapping("/v1/admin/partners/{partnerId}/location-history")
    List<PartnerTrackingStore.Location> history(Caller caller, @PathVariable UUID partnerId,
                                                 @RequestParam(defaultValue = "100") int limit) {
        return tracking.history(caller, partnerId, limit);
    }

    @GetMapping("/v1/partners/me/location-history")
    List<PartnerTrackingStore.Location> ownHistory(Caller caller,
                                                    @RequestParam(defaultValue = "100") int limit) {
        return tracking.ownHistory(caller, limit);
    }
}

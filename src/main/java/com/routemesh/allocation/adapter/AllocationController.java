package com.routemesh.allocation.adapter;

import com.routemesh.allocation.application.AllocationService;
import com.routemesh.booking.application.BookingService.PartnerDecision;
import com.routemesh.booking.application.BookingStore.Booking;
import com.routemesh.shared.security.Caller;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
class AllocationController {
    private final AllocationService allocation;

    AllocationController(AllocationService allocation) {
        this.allocation = allocation;
    }

    @PostMapping("/v1/partners/me/bookings/{id}/accept")
    Booking accept(Caller caller, @PathVariable UUID id, @Valid @RequestBody PartnerDecision in) {
        return allocation.respond(caller, id, in.expectedVersion(), true, in.reason());
    }

    @PostMapping("/v1/partners/me/bookings/{id}/reject")
    Booking reject(Caller caller, @PathVariable UUID id, @Valid @RequestBody PartnerDecision in) {
        return allocation.respond(caller, id, in.expectedVersion(), false, in.reason());
    }
}

/*
 * Copyright (c) 2026 SathiHomeCare. All Rights Reserved.
 * Project: SathiRoute | Developer: Abhishek Pandey
 * Subject to verification of ownership and applicable third-party licenses.
 */
package com.routemesh.booking.adapter;

import com.routemesh.booking.application.BookingService;
import com.routemesh.booking.application.ServiceExecutionService;
import com.routemesh.booking.application.BookingService.CancelBooking;
import com.routemesh.booking.application.BookingService.AssignBooking;
import com.routemesh.booking.application.BookingService.CustomerInput;
import com.routemesh.booking.application.BookingService.NewBooking;
import com.routemesh.booking.application.BookingService.PartnerDecision;
import com.routemesh.booking.application.BookingService.RejectBooking;
import com.routemesh.booking.application.BookingService.UpdateBooking;
import com.routemesh.booking.application.BookingStore.Booking;
import com.routemesh.booking.application.BookingStore.Customer;
import com.routemesh.idempotency.adapter.IdempotentResponses;
import com.routemesh.shared.error.ApiException;
import com.routemesh.shared.security.Caller;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
class BookingController {
    private final BookingService bookings;
    private final ServiceExecutionService execution;

    BookingController(BookingService bookings, ServiceExecutionService execution) {
        this.bookings = bookings;
        this.execution = execution;
    }

    /** Customer authentication/token issuance is delegated to the configured OIDC provider. */
    @PostMapping("/v1/customers")
    @ResponseStatus(HttpStatus.CREATED)
    Customer registerCustomer(Caller caller, @Valid @RequestBody CustomerInput in) {
        return bookings.registerCustomer(caller, in);
    }

    @GetMapping("/v1/customers/me")
    Customer customer(Caller caller) {
        return bookings.customer(caller);
    }

    @PatchMapping("/v1/customers/me")
    Customer updateCustomer(Caller caller, @Valid @RequestBody CustomerInput in) {
        return bookings.updateCustomer(caller, in);
    }

    @PostMapping("/v1/bookings")
    ResponseEntity<Booking> create(Caller caller,
                                   @RequestHeader(name = IdempotentResponses.KEY_HEADER, required = false) String key,
                                   @Valid @RequestBody NewBooking in) {
        return IdempotentResponses.of(bookings.create(caller, key, in), HttpStatus.CREATED);
    }

    @GetMapping("/v1/bookings")
    List<Booking> history(Caller caller, @RequestParam(defaultValue = "50") int limit,
                          @RequestParam(defaultValue = "0") int offset) {
        return bookings.history(caller, limit, offset);
    }

    @GetMapping("/v1/admin/bookings")
    List<Booking> adminBookings(Caller caller, @RequestParam(defaultValue = "100") int limit,
                                @RequestParam(defaultValue = "0") int offset) {
        return bookings.adminBookings(caller, limit, offset);
    }

    @GetMapping("/v1/bookings/{id}")
    Booking get(Caller caller, @PathVariable UUID id) {
        return bookings.get(caller, id);
    }

    @PatchMapping("/v1/bookings/{id}")
    Booking update(Caller caller, @PathVariable UUID id, @Valid @RequestBody UpdateBooking in) {
        return bookings.update(caller, id, in);
    }

    @PatchMapping("/v1/bookings/{id}/cancel")
    Booking cancel(Caller caller, @PathVariable UUID id, @Valid @RequestBody CancelBooking in) {
        return bookings.cancel(caller, id, in);
    }

    @PostMapping("/v1/admin/bookings/{id}/allocation-start")
    Booking beginAllocation(Caller caller, @PathVariable UUID id) {
        return bookings.beginAllocation(caller, id);
    }

    @PostMapping("/v1/admin/bookings/{id}/reject")
    Booking reject(Caller caller, @PathVariable UUID id, @Valid @RequestBody RejectBooking in) {
        return bookings.reject(caller, id, in);
    }

    @PatchMapping("/v1/admin/bookings/{id}/assign")
    Booking assign(Caller caller, @PathVariable UUID id, @Valid @RequestBody AssignBooking in) {
        return bookings.assign(caller, id, in);
    }

    @GetMapping("/v1/partners/me/bookings")
    List<Booking> partnerBookings(Caller caller, @RequestParam(defaultValue = "50") int limit,
                                  @RequestParam(defaultValue = "0") int offset) {
        return bookings.partnerBookings(caller, limit, offset);
    }

    @GetMapping("/v1/partners/me/bookings/{id}")
    Booking partnerBooking(Caller caller, @PathVariable UUID id) {
        return bookings.partnerBooking(caller, id);
    }

    @PostMapping("/v1/partners/me/bookings/{id}/start")
    ServiceExecutionService.ExecutionView start(Caller caller, @PathVariable UUID id,
            @Valid @RequestBody PartnerDecision in) {
        return execution.start(caller, id, new ServiceExecutionService.PartnerAction(in.expectedVersion(), in.reason()));
    }

    @PostMapping("/v1/partners/me/bookings/{id}/complete")
    ServiceExecutionService.ExecutionView complete(Caller caller, @PathVariable UUID id,
            @Valid @RequestBody ServiceExecutionService.CompleteIn in) {
        return execution.complete(caller, id, in);
    }
}

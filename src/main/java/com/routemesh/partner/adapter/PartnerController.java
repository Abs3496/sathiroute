/*
 * Copyright (c) 2026 SathiHomeCare. All Rights Reserved.
 * Project: SathiRoute | Developer: Abhishek Pandey
 * Subject to verification of ownership and applicable third-party licenses.
 */
package com.routemesh.partner.adapter;

import com.routemesh.partner.application.PartnerService;
import com.routemesh.partner.application.PartnerService.ProfileUpdate;
import com.routemesh.partner.application.PartnerService.Registration;
import com.routemesh.partner.application.PartnerService.SkillInput;
import com.routemesh.partner.application.PartnerStore.BookingHistory;
import com.routemesh.partner.application.PartnerStore.Profile;
import com.routemesh.partner.application.PartnerStore.Workload;
import com.routemesh.partner.domain.PartnerAvailability;
import com.routemesh.shared.error.ApiException;
import com.routemesh.shared.security.Caller;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
class PartnerController {
    private final PartnerService partners;

    PartnerController(PartnerService partners) {
        this.partners = partners;
    }

    /** Registration binds this profile to the caller's authenticated OIDC subject; the app never handles passwords. */
    @PostMapping("/v1/partners")
    @ResponseStatus(HttpStatus.CREATED)
    Profile register(Caller caller, @Valid @RequestBody Registration in) {
        if (in == null) throw ApiException.invalid("request body is required");
        return partners.register(caller, in);
    }

    @GetMapping("/v1/partners/me")
    Profile me(Caller caller) {
        return partners.profile(caller);
    }

    @PatchMapping("/v1/partners/me")
    Profile update(Caller caller, @Valid @RequestBody ProfileUpdate in) {
        if (in == null) throw ApiException.invalid("request body is required");
        return partners.updateProfile(caller, in);
    }

    @PutMapping("/v1/partners/me/skills")
    Profile skills(Caller caller, @Valid @RequestBody SkillsIn in) {
        if (in == null) throw ApiException.invalid("request body is required");
        return partners.updateSkills(caller, in.skills());
    }

    @PutMapping("/v1/partners/me/availability")
    Profile availability(Caller caller, @Valid @RequestBody AvailabilityIn in) {
        if (in == null) throw ApiException.invalid("request body is required");
        return partners.updateAvailability(caller, in.windows());
    }

    @PatchMapping("/v1/partners/me/availability/status")
    Profile availabilityStatus(Caller caller, @Valid @RequestBody AvailabilityStatusIn in) {
        if (in == null || in.available() == null) throw ApiException.invalid("available is required");
        return partners.setAvailable(caller, in.available());
    }

    @PatchMapping("/v1/partners/me/location")
    Profile location(Caller caller, @Valid @RequestBody LocationIn in) {
        if (in == null || in.latitude() == null || in.longitude() == null) {
            throw ApiException.invalid("latitude and longitude are required");
        }
        return partners.updateLocation(caller, in.latitude(), in.longitude());
    }

    @PostMapping("/v1/partners/me/time-off")
    @ResponseStatus(HttpStatus.CREATED)
    void addTimeOff(Caller caller, @Valid @RequestBody TimeOffIn in) {
        if (in == null || in.date() == null) throw ApiException.invalid("date is required");
        partners.addTimeOff(caller, in.date(), in.reason());
    }

    @DeleteMapping("/v1/partners/me/time-off/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeTimeOff(Caller caller, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        partners.removeTimeOff(caller, date);
    }

    @GetMapping("/v1/partners/me/time-off")
    List<LocalDate> timeOff(Caller caller,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return partners.timeOff(caller, from, to);
    }

    @GetMapping("/v1/partners/me/history")
    List<BookingHistory> history(Caller caller, @RequestParam(defaultValue = "50") int limit) {
        return partners.history(caller, limit);
    }

    @GetMapping("/v1/partners/me/workload")
    Workload workload(Caller caller) {
        return partners.workload(caller);
    }

    @GetMapping("/v1/admin/partners")
    List<Profile> adminList(Caller caller, @RequestParam(required = false) String status) {
        return partners.adminList(caller, status);
    }

    @PostMapping("/v1/admin/partners")
    @ResponseStatus(HttpStatus.CREATED)
    Profile createManual(Caller caller, @Valid @RequestBody Registration in) {
        return partners.createManual(caller, in);
    }

    @PatchMapping("/v1/admin/partners/{id}/verification")
    Profile verify(Caller caller, @PathVariable UUID id, @Valid @RequestBody VerificationIn in) {
        if (in == null || in.approved() == null) throw ApiException.invalid("approved is required");
        return partners.verify(caller, id, in.approved());
    }

    @PatchMapping("/v1/admin/partners/{id}/status")
    Profile active(Caller caller, @PathVariable UUID id, @Valid @RequestBody ActiveIn in) {
        if (in == null || in.active() == null) throw ApiException.invalid("active is required");
        return partners.setActive(caller, id, in.active());
    }

    record SkillsIn(@Valid List<SkillInput> skills) { }
    record AvailabilityIn(@Valid List<PartnerAvailability> windows) { }
    record AvailabilityStatusIn(@jakarta.validation.constraints.NotNull Boolean available) { }
    record LocationIn(@jakarta.validation.constraints.NotNull Double latitude,
                      @jakarta.validation.constraints.NotNull Double longitude) { }
    record TimeOffIn(@jakarta.validation.constraints.NotNull LocalDate date,
                     @jakarta.validation.constraints.Size(max = 1000) String reason) { }
    record VerificationIn(@jakarta.validation.constraints.NotNull Boolean approved) { }
    record ActiveIn(@jakarta.validation.constraints.NotNull Boolean active) { }
}

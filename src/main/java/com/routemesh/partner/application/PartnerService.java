/*
 * Copyright (c) 2026 SathiHomeCare. All Rights Reserved.
 * Project: SathiRoute | Developer: Abhishek Pandey
 * Subject to verification of ownership and applicable third-party licenses.
 */
package com.routemesh.partner.application;

import com.routemesh.partner.application.PartnerStore.BookingHistory;
import com.routemesh.partner.application.PartnerStore.Profile;
import com.routemesh.partner.application.PartnerStore.Skill;
import com.routemesh.partner.application.PartnerStore.Workload;
import com.routemesh.partner.domain.PartnerAvailability;
import com.routemesh.shared.error.ApiException;
import com.routemesh.shared.security.Caller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PartnerService {
    private static final Set<String> CATEGORIES = Set.of("NURSING", "AYURVEDIC_THERAPY", "COUNSELLING");
    private final PartnerStore store;
    private final Clock clock;

    public PartnerService(PartnerStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    @Transactional
    public Profile register(Caller caller, Registration in) {
        caller.requireServicePartner();
        requireText(in.name(), "name");
        requireText(in.email(), "email");
        requireText(in.phone(), "phone");
        requireText(in.qualificationDetails(), "qualificationDetails");
        validateRadius(in.serviceRadiusKm());
        if (!store.centreExists(in.centreId())) {
            throw ApiException.invalid("unknown-centre", "operating centre not found");
        }
        var skills = validateSkills(in.skills(), false);
        UUID id = UUID.randomUUID();
        String code = "SP-" + id.toString().substring(0, 8).toUpperCase();
        try {
            store.create(id, caller.subject(), in.centreId(), code, in.name().trim(), in.email().trim(),
                    in.phone().trim(), in.qualificationDetails().trim(), in.serviceRadiusKm(), clock.instant(), skills);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("partner-already-registered", "this login already has a partner profile");
        }
        return profile(caller);
    }

    @Transactional
    public Profile createManual(Caller caller, Registration in) {
        caller.requireAdmin();
        if (in == null) throw ApiException.invalid("partner details are required");
        requireText(in.name(), "name");
        requireText(in.email(), "email");
        requireText(in.phone(), "phone");
        requireText(in.qualificationDetails(), "qualificationDetails");
        validateRadius(in.serviceRadiusKm());
        if (!store.centreExists(in.centreId())) throw ApiException.invalid("unknown-centre", "operating centre not found");
        var skills = validateSkills(in.skills(), false);
        UUID id = UUID.randomUUID();
        String code = "SP-" + id.toString().substring(0, 8).toUpperCase();
        try {
            store.create(id, null, in.centreId(), code, in.name().trim(), in.email().trim(), in.phone().trim(),
                    in.qualificationDetails().trim(), in.serviceRadiusKm(), clock.instant(), skills);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("partner-already-registered", "partner email or code is already registered");
        }
        return store.findById(id).orElseThrow(() -> ApiException.notFound("partner"));
    }

    public Profile profile(Caller caller) {
        caller.requireServicePartner();
        return own(caller);
    }

    @Transactional
    public Profile updateProfile(Caller caller, ProfileUpdate in) {
        caller.requireServicePartner();
        requireText(in.name(), "name");
        requireText(in.email(), "email");
        requireText(in.phone(), "phone");
        requireText(in.qualificationDetails(), "qualificationDetails");
        validateRadius(in.serviceRadiusKm());
        store.updateProfile(caller.subject(), in.name().trim(), in.email().trim(), in.phone().trim(),
                in.qualificationDetails().trim(), in.serviceRadiusKm());
        return own(caller);
    }

    @Transactional
    public Profile updateSkills(Caller caller, List<SkillInput> inputs) {
        caller.requireServicePartner();
        var skills = validateSkills(inputs, false);
        store.replaceSkills(caller.subject(), skills);
        return own(caller);
    }

    @Transactional
    public Profile updateAvailability(Caller caller, List<PartnerAvailability> availability) {
        caller.requireServicePartner();
        if (availability == null) {
            throw ApiException.invalid("availability is required");
        }
        var copy = List.copyOf(availability);
        validateNoOverlap(copy);
        store.replaceAvailability(caller.subject(), copy);
        return own(caller);
    }

    @Transactional
    public Profile setAvailable(Caller caller, boolean available) {
        caller.requireServicePartner();
        store.setAvailability(caller.subject(), available);
        return own(caller);
    }

    @Transactional
    public Profile updateLocation(Caller caller, double latitude, double longitude) {
        caller.requireServicePartner();
        try {
            var point = new com.routemesh.geo.domain.GeoPoint(latitude, longitude);
            store.setLocation(caller.subject(), point.lat(), point.lng(), clock.instant());
        } catch (IllegalArgumentException e) {
            throw ApiException.invalid("bad-position", e.getMessage());
        }
        return own(caller);
    }

    @Transactional
    public void addTimeOff(Caller caller, LocalDate date, String reason) {
        caller.requireServicePartner();
        if (date == null || date.isBefore(LocalDate.now(clock))) {
            throw ApiException.invalid("bad-date", "unavailable date must be today or in the future");
        }
        store.addTimeOff(caller.subject(), date, reason, clock.instant());
    }

    @Transactional
    public void removeTimeOff(Caller caller, LocalDate date) {
        caller.requireServicePartner();
        store.removeTimeOff(caller.subject(), date);
    }

    public List<LocalDate> timeOff(Caller caller, LocalDate from, LocalDate to) {
        caller.requireServicePartner();
        if (from == null || to == null || to.isBefore(from)) {
            throw ApiException.invalid("bad-date-range", "from and to are required and to must not precede from");
        }
        own(caller);
        return store.timeOff(caller.subject(), from, to);
    }

    public List<Profile> adminList(Caller caller, String status) {
        caller.requireAdmin();
        if (status != null && !Set.of("PENDING", "APPROVED", "REJECTED").contains(status)) {
            throw ApiException.invalid("bad-registration-status", "status must be PENDING, APPROVED or REJECTED");
        }
        return store.findAll(status);
    }

    @Transactional
    public Profile verify(Caller caller, UUID id, boolean approved) {
        caller.requireAdmin();
        Profile profile = store.findById(id).orElseThrow(() -> ApiException.notFound("partner"));
        if (approved && (profile.skills().isEmpty() || profile.qualificationDetails() == null
                || profile.qualificationDetails().isBlank())) {
            throw ApiException.conflict("incomplete-qualification", "partner must provide skills and qualification details");
        }
        store.decideVerification(id, approved, caller.subject(), clock.instant());
        return store.findById(id).orElseThrow(() -> ApiException.notFound("partner"));
    }

    @Transactional
    public Profile setActive(Caller caller, UUID id, boolean active) {
        caller.requireAdmin();
        Profile profile = store.findById(id).orElseThrow(() -> ApiException.notFound("partner"));
        if (active && !"APPROVED".equals(profile.registrationStatus())) {
            throw ApiException.conflict("partner-not-approved", "only an approved partner can be activated");
        }
        store.setActive(id, active);
        return store.findById(id).orElseThrow(() -> ApiException.notFound("partner"));
    }

    public List<BookingHistory> history(Caller caller, int limit) {
        caller.requireServicePartner();
        if (limit < 1 || limit > 100) {
            throw ApiException.invalid("limit must be between 1 and 100");
        }
        return store.history(own(caller).id(), limit);
    }

    public Workload workload(Caller caller) {
        caller.requireServicePartner();
        return store.workload(own(caller).id(), clock.instant());
    }

    private Profile own(Caller caller) {
        return store.findBySubject(caller.subject()).orElseThrow(() -> ApiException.notFound("partner profile"));
    }

    private static List<Skill> validateSkills(List<SkillInput> inputs, boolean verified) {
        if (inputs == null || inputs.isEmpty()) {
            throw ApiException.invalid("skills-required", "at least one service skill is required");
        }
        var out = new ArrayList<Skill>();
        var seen = new HashSet<String>();
        for (var input : inputs) {
            if (input == null || input.category() == null || !CATEGORIES.contains(input.category())
                    || input.code() == null || input.code().isBlank() || input.qualificationDetails() == null
                    || input.qualificationDetails().isBlank()) {
                throw ApiException.invalid("bad-skill", "each skill needs a supported category, code and qualification details");
            }
            String category = input.category();
            String code = input.code().trim();
            if (!seen.add(category + ":" + code)) {
                throw ApiException.invalid("duplicate-skill", "duplicate category and skill code");
            }
            out.add(new Skill(category, code, input.qualificationDetails().trim(), verified));
        }
        return List.copyOf(out);
    }

    private static void validateNoOverlap(List<PartnerAvailability> entries) {
        var sorted = entries.stream().sorted(Comparator.comparingInt(PartnerAvailability::dayOfWeek)
                .thenComparing(PartnerAvailability::startsAt)).toList();
        for (int i = 1; i < sorted.size(); i++) {
            var previous = sorted.get(i - 1);
            var current = sorted.get(i);
            if (previous.dayOfWeek() == current.dayOfWeek() && current.startsAt().isBefore(previous.endsAt())) {
                throw ApiException.invalid("availability-overlap", "working time ranges cannot overlap");
            }
        }
    }

    private static void validateRadius(double radiusKm) {
        if (!Double.isFinite(radiusKm) || radiusKm <= 0 || radiusKm > 500) {
            throw ApiException.invalid("bad-service-radius", "service radius must be greater than 0 and at most 500 km");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw ApiException.invalid(name + " is required");
        }
    }

    public record Registration(@NotNull UUID centreId, @NotBlank String name, @NotBlank @Email String email,
                               @NotBlank String phone, @NotBlank String qualificationDetails,
                               @DecimalMin(value = "0", inclusive = false) @DecimalMax("500") double serviceRadiusKm,
                               @NotNull List<@Valid SkillInput> skills) { }
    public record ProfileUpdate(@NotBlank String name, @NotBlank @Email String email, @NotBlank String phone,
                                @NotBlank String qualificationDetails,
                                @DecimalMin(value = "0", inclusive = false) @DecimalMax("500") double serviceRadiusKm) { }
    public record SkillInput(@NotBlank @Pattern(regexp = "NURSING|AYURVEDIC_THERAPY|COUNSELLING") String category,
                             @NotBlank String code, @NotBlank String qualificationDetails) { }
}

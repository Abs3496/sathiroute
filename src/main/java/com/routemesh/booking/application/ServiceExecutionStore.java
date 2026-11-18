package com.routemesh.booking.application;

import com.routemesh.booking.domain.BookingStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceExecutionStore {
    record Execution(UUID bookingId, UUID partnerId, Instant arrivedAt, Instant startedAt,
                     Instant completionSubmittedAt, Instant completedAt, Instant customerConfirmedAt,
                     boolean otpRequired, boolean otpVerified, String completionNotes, int otpAttempts, long version) { }
    record Event(long id, String type, String actorSubject, Instant occurredAt, String details) { }
    record Evidence(UUID id, UUID bookingId, String uploadedBy, String fileName, String contentType,
                    String sha256, int bytes, Instant uploadedAt) { }
    record EvidenceContent(Evidence evidence, byte[] content) { }
    record OtpResult(boolean confirmed, boolean locked) { }
    record Monitor(UUID bookingId, UUID partnerId, String partnerName, String customerName,
                   BookingStatus bookingStatus, Instant appointmentStart, Instant arrivedAt, Instant startedAt,
                   Instant completedAt, Instant customerConfirmedAt, boolean otpRequired, boolean otpVerified) { }

    Optional<Execution> find(UUID bookingId);
    void arrival(UUID bookingId, UUID partnerId, Instant now, String actor);
    void start(UUID bookingId, Instant now, String actor);
    void submitCompletion(UUID bookingId, Instant now, String notes, boolean otpRequired, String otpHash, String actor);
    OtpResult verifyCustomerOtp(UUID bookingId, String candidateHash, Instant now, String actor);
    boolean confirmWithoutOtp(UUID bookingId, Instant now, String actor);
    void addEvidence(EvidenceContent evidence);
    List<Evidence> evidence(UUID bookingId);
    Optional<EvidenceContent> evidence(UUID bookingId, UUID evidenceId);
    List<Event> events(UUID bookingId);
    List<Monitor> monitor(String status);
}

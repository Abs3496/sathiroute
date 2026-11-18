package com.routemesh.booking.application;

import com.routemesh.booking.application.BookingStore.Booking;
import com.routemesh.booking.application.ServiceExecutionStore.Evidence;
import com.routemesh.booking.application.ServiceExecutionStore.EvidenceContent;
import com.routemesh.booking.domain.BookingStatus;
import com.routemesh.booking.domain.ServiceCompletionCode;
import com.routemesh.shared.config.RouteMeshProperties;
import com.routemesh.shared.error.ApiException;
import com.routemesh.shared.mail.Mailer;
import com.routemesh.shared.security.Caller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ServiceExecutionService {
    public record PartnerAction(long expectedVersion, String reason) { }
    public record CompleteIn(long expectedVersion, String completionNotes, String reason) { }
    public record CompletionConfirmation(String otp, Boolean confirmed) { }
    public record EvidenceIn(String fileName, String contentType, String contentBase64) { }
    public record EventView(long id, String type, String actor, Instant occurredAt, String details) { }
    public record EvidenceView(UUID id, UUID bookingId, String uploadedBy, String fileName, String contentType,
                               String sha256, int bytes, Instant uploadedAt) { }
    public record EvidenceDownload(EvidenceView evidence, byte[] content) { }
    public record ExecutionView(UUID bookingId, BookingStatus bookingStatus, ServiceExecutionStore.Execution execution,
                                List<EventView> events, List<EvidenceView> evidence) { }
    public record ConfirmationView(UUID bookingId, BookingStatus bookingStatus, Instant customerConfirmedAt) { }

    private record ConfirmOutcome(boolean success, boolean locked, Booking booking) { }

    private final BookingStore bookings;
    private final ServiceExecutionStore executions;
    private final RouteMeshProperties properties;
    private final Clock clock;
    private final Mailer mailer;
    private final TransactionTemplate tx;
    private final boolean otpRequired;
    private final int maxEvidenceBytes;

    public ServiceExecutionService(BookingStore bookings, ServiceExecutionStore executions,
            RouteMeshProperties properties, Clock clock, Mailer mailer, PlatformTransactionManager txManager,
            @Value("${routemesh.service.completion-otp-required:false}") boolean otpRequired,
            @Value("${routemesh.service.evidence-max-bytes:2000000}") int maxEvidenceBytes) {
        this.bookings = bookings; this.executions = executions;
        this.properties = properties; this.clock = clock; this.mailer = mailer;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.otpRequired = otpRequired;
        this.maxEvidenceBytes = maxEvidenceBytes;
    }

    @Transactional
    public ExecutionView arrive(Caller caller, UUID bookingId, PartnerAction in) {
        validateAction(in);
        Booking booking = assignedBooking(caller, bookingId);
        if (booking.version() != in.expectedVersion()) throw versionConflict();
        if (booking.status() != BookingStatus.CONFIRMED && booking.status() != BookingStatus.PARTNER_ON_THE_WAY
                && booking.status() != BookingStatus.PARTNER_NEARBY && booking.status() != BookingStatus.PARTNER_ARRIVED) {
            throw ApiException.conflict("arrival-not-allowed", "partner must accept the booking before confirming arrival");
        }
        Booking moved = booking;
        while (moved.status() != BookingStatus.PARTNER_ARRIVED) {
            BookingStatus next = switch (moved.status()) {
                case CONFIRMED -> BookingStatus.PARTNER_ON_THE_WAY;
                case PARTNER_ON_THE_WAY -> BookingStatus.PARTNER_NEARBY;
                case PARTNER_NEARBY -> BookingStatus.PARTNER_ARRIVED;
                default -> throw ApiException.conflict("arrival-not-allowed", "booking cannot move to arrived");
            };
            moved = bookings.transitionAsPartner(bookingId, moved.version(), moved.status(), next,
                    caller.subject(), reason(in.reason(), "partner arrival confirmed"), clock.instant());
        }
        Instant now = clock.instant();
        executions.arrival(bookingId, booking.assignedPartnerId(), now, caller.subject());
        return view(moved);
    }

    @Transactional
    public ExecutionView start(Caller caller, UUID bookingId, PartnerAction in) {
        validateAction(in);
        Booking booking = assignedBooking(caller, bookingId);
        if (booking.version() != in.expectedVersion()) throw versionConflict();
        if (booking.status() != BookingStatus.PARTNER_ARRIVED) {
            throw ApiException.conflict("arrival-required", "confirm partner arrival before starting service");
        }
        Instant now = clock.instant();
        executions.arrival(bookingId, booking.assignedPartnerId(), now, caller.subject());
        executions.start(bookingId, now, caller.subject());
        Booking moved = bookings.transitionAsPartner(bookingId, in.expectedVersion(), booking.status(),
                BookingStatus.IN_PROGRESS, caller.subject(), reason(in.reason(), "service started"), now);
        return view(moved);
    }

    @Transactional
    public ExecutionView complete(Caller caller, UUID bookingId, CompleteIn in) {
        if (in == null || in.expectedVersion() < 0 || in.completionNotes() != null && in.completionNotes().length() > 4000
                || in.reason() != null && in.reason().length() > 1000) {
            throw ApiException.invalid("expectedVersion and notes/reason within their limits are required");
        }
        Booking booking = assignedBooking(caller, bookingId);
        if (booking.version() != in.expectedVersion()) throw versionConflict();
        if (booking.status() != BookingStatus.IN_PROGRESS) throw ApiException.conflict("service-not-in-progress", "only an in-progress service can be completed");
        if (executions.find(bookingId).map(ServiceExecutionStore.Execution::completedAt).orElse(null) != null)
            throw ApiException.conflict("completion-already-submitted", "customer confirmation is still pending");
        Instant now = clock.instant();
        String code = otpRequired ? ServiceCompletionCode.newCode() : null;
        String hash = otpRequired ? codeHelper().hash(bookingId, code) : null;
        executions.submitCompletion(bookingId, now, in.completionNotes(), otpRequired, hash, caller.subject());
        if (otpRequired) sendCompletionCodeAfterCommit(booking, code);
        return view(booking);
    }

    public ConfirmationView confirmCompletion(Caller caller, UUID bookingId, CompletionConfirmation in) {
        if (in == null || in.otp() != null && !in.otp().matches("\\d{6}")) {
            throw ApiException.invalid("provide a six-digit otp or explicit customer confirmation");
        }
        Booking current = customerBooking(caller, bookingId);
        var execution = executions.find(bookingId).orElseThrow(() -> ApiException.conflict("completion-not-submitted", "partner has not submitted service completion"));
        if (execution.otpRequired() && (in.otp() == null || in.otp().isBlank())) {
            throw ApiException.invalid("completion otp is required");
        }
        if (!execution.otpRequired() && !Boolean.TRUE.equals(in.confirmed())) {
            throw ApiException.invalid("confirmed=true is required when otp verification is not enabled");
        }
        String candidate = execution.otpRequired() ? codeHelper().hash(bookingId, in.otp()) : null;
        ConfirmOutcome outcome = tx.execute(status -> {
            Instant now = clock.instant();
            boolean success;
            boolean locked = false;
            if (execution.otpRequired()) {
                var result = executions.verifyCustomerOtp(bookingId, candidate, now, caller.subject());
                success = result.confirmed(); locked = result.locked();
            } else {
                success = executions.confirmWithoutOtp(bookingId, now, caller.subject());
            }
            Booking latestBooking = bookings.booking(bookingId).orElseThrow(() -> ApiException.notFound("booking"));
            if (success && latestBooking.status() == BookingStatus.IN_PROGRESS) {
                latestBooking = bookings.transition(bookingId, latestBooking.version(), BookingStatus.IN_PROGRESS,
                        BookingStatus.COMPLETED, caller.subject(), "customer confirmed service completion", now);
            }
            return new ConfirmOutcome(success, locked, latestBooking);
        });
        if (outcome == null || !outcome.success()) {
            throw ApiException.unprocessable(outcome != null && outcome.locked() ? "completion-otp-locked" : "wrong-completion-otp",
                    outcome != null && outcome.locked() ? "completion confirmation is locked after five attempts" : "completion code is wrong or expired");
        }
        var done = executions.find(bookingId).orElseThrow();
        return new ConfirmationView(bookingId, outcome.booking().status(), done.customerConfirmedAt());
    }

    @Transactional
    public EvidenceView addEvidence(Caller caller, UUID bookingId, EvidenceIn in) {
        if (in == null || in.fileName() == null || in.fileName().isBlank() || in.fileName().length() > 200
                || in.fileName().contains("/") || in.fileName().contains("\\") || in.contentBase64() == null
                || in.contentType() == null || in.fileName().chars().anyMatch(Character::isISOControl))
            throw ApiException.invalid("fileName, contentType and contentBase64 are required");
        Booking booking = assignedBooking(caller, bookingId);
        if (booking.status() != BookingStatus.IN_PROGRESS) throw ApiException.conflict("service-not-in-progress", "evidence can only be added during service");
        if (executions.find(bookingId).map(ServiceExecutionStore.Execution::completedAt).orElse(null) != null)
            throw ApiException.conflict("completion-submitted", "evidence cannot be added after completion was submitted");
        byte[] content;
        try { content = Base64.getDecoder().decode(in.contentBase64()); }
        catch (IllegalArgumentException e) { throw ApiException.invalid("bad-evidence", "contentBase64 is invalid"); }
        if (content.length == 0 || content.length > maxEvidenceBytes) throw new ApiException(413, "evidence-too-large", "evidence exceeds configured byte limit");
        validateEvidence(in.contentType(), content);
        var evidence = new Evidence(UUID.randomUUID(), bookingId, caller.subject(), in.fileName().trim(), in.contentType(),
                sha256(content), content.length, clock.instant());
        executions.addEvidence(new EvidenceContent(evidence, content));
        return evidenceView(evidence);
    }

    public List<EvidenceView> evidence(Caller caller, UUID bookingId) {
        authorizeExecutionRead(caller, bookingId);
        return executions.evidence(bookingId).stream().map(this::evidenceView).toList();
    }

    public EvidenceDownload evidence(Caller caller, UUID bookingId, UUID evidenceId) {
        authorizeExecutionRead(caller, bookingId);
        var content = executions.evidence(bookingId, evidenceId).orElseThrow(() -> ApiException.notFound("service evidence"));
        return new EvidenceDownload(evidenceView(content.evidence()), content.content());
    }

    public ExecutionView get(Caller caller, UUID bookingId) {
        Booking booking = authorizeExecutionRead(caller, bookingId);
        return view(booking);
    }

    public List<ServiceExecutionStore.Monitor> monitor(Caller caller, String status) {
        caller.requireOperations();
        if (status != null) {
            try { BookingStatus.valueOf(status); }
            catch (IllegalArgumentException e) { throw ApiException.invalid("unknown booking status"); }
        }
        return executions.monitor(status);
    }

    private ExecutionView view(Booking booking) {
        return new ExecutionView(booking.id(), booking.status(), executions.find(booking.id()).orElse(null),
                executions.events(booking.id()).stream().map(e -> new EventView(e.id(), e.type(), actorLabel(booking, e.actorSubject()),
                        e.occurredAt(), e.details())).toList(),
                executions.evidence(booking.id()).stream().map(this::evidenceView).toList());
    }

    private EvidenceView evidenceView(Evidence evidence) {
        return new EvidenceView(evidence.id(), evidence.bookingId(), "PARTNER", evidence.fileName(), evidence.contentType(),
                evidence.sha256(), evidence.bytes(), evidence.uploadedAt());
    }

    private String actorLabel(Booking booking, String subject) {
        if (subject.equals(booking.customerSubject())) return "CUSTOMER";
        if (bookings.partnerOwns(subject, booking.id())) return "PARTNER";
        return subject.startsWith("system:") ? "SYSTEM" : "DISPATCHER";
    }

    private Booking authorizeExecutionRead(Caller caller, UUID bookingId) {
        Booking booking = bookings.booking(bookingId).orElseThrow(() -> ApiException.notFound("booking"));
        if (caller.isDispatcher()) return booking;
        if ((caller.roles().contains(com.routemesh.shared.security.Role.CUSTOMER)
                || caller.roles().contains(com.routemesh.shared.security.Role.SHIPPER))
                && booking.customerSubject().equals(caller.subject())) return booking;
        if (caller.roles().contains(com.routemesh.shared.security.Role.SERVICE_PARTNER)
                && bookings.partnerOwns(caller.subject(), bookingId)) return booking;
        throw ApiException.notFound("booking");
    }

    private Booking assignedBooking(Caller caller, UUID bookingId) {
        caller.requireServicePartner();
        return bookings.partnerBooking(caller.subject(), bookingId).orElseThrow(() -> ApiException.notFound("assigned booking"));
    }

    private Booking customerBooking(Caller caller, UUID bookingId) {
        caller.requireCustomer();
        Booking booking = bookings.booking(bookingId).filter(b -> b.customerSubject().equals(caller.subject()))
                .orElseThrow(() -> ApiException.notFound("booking"));
        return booking;
    }

    private void sendCompletionCodeAfterCommit(Booking booking, String code) {
        sendAfterCommit(List.of(booking.customerEmail()), "Confirm your SathiHomeCare service",
                "Hello " + booking.customerName() + ", your partner submitted service completion. Share this six-digit code to confirm: " + code);
    }

    private void sendAfterCommit(List<String> recipients, String subject, String body) {
        Runnable send = () -> {
            try { mailer.send(recipients, subject, body, null); }
            catch (RuntimeException ignored) { /* an operational mail retry can be added without exposing OTPs in logs */ }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else send.run();
    }

    private ServiceCompletionCode codeHelper() {
        return new ServiceCompletionCode(properties.security().otpKey());
    }

    private static void validateAction(PartnerAction in) {
        if (in == null || in.expectedVersion() < 0 || in.reason() != null && in.reason().length() > 1000)
            throw ApiException.invalid("expectedVersion and an optional reason up to 1000 characters are required");
    }

    private static String reason(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static ApiException versionConflict() { return ApiException.conflict("version-conflict", "booking changed; reload and retry"); }

    private static void validateEvidence(String type, byte[] bytes) {
        boolean valid = switch (type) {
            case "image/jpeg" -> bytes.length > 3 && (bytes[0]&0xff)==0xff && (bytes[1]&0xff)==0xd8 && (bytes[2]&0xff)==0xff;
            case "image/png" -> bytes.length > 8 && (bytes[0]&0xff)==0x89 && bytes[1]=='P' && bytes[2]=='N' && bytes[3]=='G';
            case "application/pdf" -> bytes.length > 4 && bytes[0]=='%' && bytes[1]=='P' && bytes[2]=='D' && bytes[3]=='F';
            default -> false;
        };
        if (!valid) throw ApiException.invalid("bad-evidence", "only valid JPEG, PNG, or PDF evidence is accepted");
    }

    private static String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}

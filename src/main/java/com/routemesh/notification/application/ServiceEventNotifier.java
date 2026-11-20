package com.routemesh.notification.application;

import com.routemesh.booking.application.BookingStore;
import com.routemesh.outbox.application.EventEnvelope;
import com.routemesh.outbox.application.ProcessedEvents;
import com.routemesh.partner.application.PartnerStore;
import com.routemesh.shared.config.RouteMeshProperties;
import com.routemesh.shared.mail.Mailer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Delivers service booking lifecycle notifications from the transactional outbox. */
@Service
public class ServiceEventNotifier {
    private static final String CONSUMER = "service-notifier";
    private final ProcessedEvents processed;
    private final BookingStore bookings;
    private final PartnerStore partners;
    private final Mailer mailer;
    private final SmsSender sms;
    private final RouteMeshProperties properties;

    public ServiceEventNotifier(ProcessedEvents processed, BookingStore bookings, PartnerStore partners,
                                Mailer mailer, SmsSender sms, RouteMeshProperties properties) {
        this.processed = processed;
        this.bookings = bookings;
        this.partners = partners;
        this.mailer = mailer;
        this.sms = sms;
        this.properties = properties;
    }

    @Transactional
    public boolean handle(EventEnvelope event) {
        String type = event.type();
        if (!isNotification(type)) return false;
        if (!processed.markProcessed(CONSUMER, event.eventId())) return false;

        UUID bookingId = UUID.fromString((String) event.data().get("bookingId"));
        var booking = bookings.booking(bookingId).orElse(null);
        if (booking == null) return true;
        String ref = booking.id().toString();
        String date = booking.appointmentStartAt().atZone(properties.planning().zone())
                .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH));
        String customer = booking.customerName();
        String ops = properties.mail().opsAddress();

        switch (type) {
            case "BOOKING_CREATED" -> send(List.of(booking.customerEmail()), "Booking received: " + ref,
                    "Hello " + customer + ", we received your SathiHomeCare booking for " + date
                            + ". We will notify you when a partner is assigned.");
            case "PARTNER_ALLOCATION_OFFER", "PARTNER_REASSIGNED" -> {
                String partnerId = (String) event.data().get("partnerId");
                if (partnerId != null) partners.findById(UUID.fromString(partnerId)).ifPresent(p -> {
                    send(List.of(p.email()), "New SathiHomeCare service offer: " + ref,
                            "A " + booking.category() + " service is scheduled for " + date
                                    + ". Sign in to review and accept or reject booking " + ref + ".");
                    if (type.equals("PARTNER_ALLOCATION_OFFER")) {
                        String update = "Hello " + customer + ", " + p.name()
                                + " has been assigned to your SathiHomeCare visit for " + date
                                + ". Partner phone: " + p.phone() + ". Booking: " + ref + ".";
                        send(List.of(booking.customerEmail()), "Care partner assigned: " + ref, update);
                        sms.send(booking.customerPhone(), update);
                    }
                    if (type.equals("PARTNER_REASSIGNED"))
                        sms.send(booking.customerPhone(), "Your SathiHomeCare visit has a new care partner: "
                                + p.name() + ", " + p.phone() + ". Appointment " + date + ". Ref " + ref + ".");
                    sms.send(p.phone(), "New SathiHomeCare service offer " + ref + ", " + date
                            + ". Sign in to review and accept or reject.");
                });
                if (type.equals("PARTNER_REASSIGNED")) {
                    send(List.of(booking.customerEmail()), "Partner updated for booking " + ref,
                            "Your service booking has been offered to another available partner. Appointment: " + date + ".");
                    send(List.of(ops), "[SathiHomeCare] Partner reassigned " + ref,
                            "Booking " + ref + " has been offered to a replacement partner.");
                }
            }
            case "PARTNER_ACCEPTED" -> {
                String confirmation = "Hello " + customer + ", your SathiHomeCare partner accepted the booking for "
                        + date + ". Booking: " + ref + ".";
                send(List.of(booking.customerEmail()), "Booking confirmed: " + ref, confirmation);
                sms.send(booking.customerPhone(), confirmation);
            }
            case "PARTNER_REJECTED" -> {
                send(List.of(booking.customerEmail()), "Partner update for booking " + ref,
                        "The offered partner could not take this booking. We are looking for another available partner.");
                send(List.of(ops), "[SathiHomeCare] Partner rejected " + ref,
                        "The partner declined booking " + ref + "; allocation will continue.");
            }
            case "ALLOCATION_OFFER_EXPIRED" -> send(List.of(ops), "[SathiHomeCare] Partner offer expired " + ref,
                    "The partner did not respond to the offer for booking " + ref + " before it expired.");
            case "ADMIN_ALERT" -> {
                send(List.of(booking.customerEmail()), "Update on your SathiHomeCare booking " + ref,
                        "We could not confirm a partner yet. Our team has been alerted and will follow up.");
                send(List.of(ops), "[SathiHomeCare] Booking needs attention " + ref,
                        "Booking status is " + event.data().get("status") + ". Reason: " + event.data().get("reason"));
            }
            case "PARTNER_ON_THE_WAY" -> customer(booking.customerEmail(), customer, ref,
                    "Your partner is on the way.");
            case "PARTNER_NEARBY" -> customer(booking.customerEmail(), customer, ref,
                    "Your partner is nearby.");
            case "PARTNER_ARRIVED" -> customer(booking.customerEmail(), customer, ref,
                    "Your partner has arrived.");
            case "SERVICE_STARTED" -> customer(booking.customerEmail(), customer, ref,
                    "Your service has started.");
            case "SERVICE_COMPLETION_SUBMITTED" -> customer(booking.customerEmail(), customer, ref,
                    "Your partner marked the service complete. Please confirm completion in your booking.");
            case "SERVICE_COMPLETED" -> customer(booking.customerEmail(), customer, ref,
                    "Service completion is confirmed. Thank you for choosing SathiHomeCare.");
            case "BOOKING_CANCELLED" -> {
                customer(booking.customerEmail(), customer, ref, "Your booking has been cancelled.");
                send(List.of(ops), "[SathiHomeCare] Booking cancelled " + ref,
                        "Booking " + ref + " was cancelled. Reason: " + event.data().get("reason"));
            }
            default -> { return false; }
        }
        return true;
    }

    private static boolean isNotification(String type) {
        return switch (type) {
            case "BOOKING_CREATED", "PARTNER_ALLOCATION_OFFER", "PARTNER_REASSIGNED", "PARTNER_ACCEPTED",
                    "PARTNER_REJECTED", "ALLOCATION_OFFER_EXPIRED", "ADMIN_ALERT", "PARTNER_ON_THE_WAY",
                    "PARTNER_NEARBY", "PARTNER_ARRIVED", "SERVICE_STARTED", "SERVICE_COMPLETION_SUBMITTED",
                    "SERVICE_COMPLETED", "BOOKING_CANCELLED" -> true;
            default -> false;
        };
    }

    private void customer(String email, String name, String bookingId, String message) {
        send(List.of(email), "SathiHomeCare booking update: " + bookingId,
                "Hello " + name + ", " + message + " Booking: " + bookingId + ".");
    }

    private void send(List<String> to, String subject, String body) {
        if (to.stream().anyMatch(value -> value == null || value.isBlank())) return;
        mailer.send(to, subject, body + "\n\nSathiHomeCare", null);
    }
}

alter table service_booking drop constraint service_booking_status_check;
alter table service_booking add constraint service_booking_status_check check (status in (
    'NEW', 'PENDING', 'ASSIGNED', 'CONFIRMED', 'PARTNER_ON_THE_WAY', 'PARTNER_NEARBY', 'PARTNER_ARRIVED',
    'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'REJECTED',
    'SEARCHING_PARTNER', 'PARTNER_OFFERED', 'EXPIRED',
    'REASSIGNMENT_REQUIRED'
));

create index service_booking_partner_tracking on service_booking (assigned_partner_id, appointment_date, status)
    where assigned_partner_id is not null;

create table service_tracking_notification (
    booking_id uuid not null references service_booking (id),
    status text not null check (status in ('PARTNER_NEARBY', 'PARTNER_ARRIVED')),
    notified_at timestamptz not null,
    primary key (booking_id, status)
);

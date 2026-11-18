create table customer_profile (
    id uuid primary key,
    auth_subject text not null unique,
    full_name text not null,
    email text not null,
    phone text not null,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

alter table service_booking
    add column customer_id uuid references customer_profile (id),
    add column appointment_date date;
update service_booking set appointment_date = appointment_start::date where appointment_date is null;
alter table service_booking alter column appointment_date set not null;
create index service_booking_customer_id on service_booking (customer_id, created_at desc);

alter table service_booking drop constraint service_booking_status_check;
alter table service_booking add constraint service_booking_status_check check (status in (
    'NEW', 'PENDING', 'ASSIGNED', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'REJECTED',
    -- Retained so rows created against the earlier schema remain valid during rollout.
    'SEARCHING_PARTNER', 'PARTNER_OFFERED', 'PARTNER_ON_THE_WAY', 'PARTNER_ARRIVED', 'EXPIRED',
    'REASSIGNMENT_REQUIRED'
));

create table service_booking_status_history (
    id bigserial primary key,
    booking_id uuid not null references service_booking (id),
    from_status text,
    to_status text not null,
    actor_subject text not null,
    reason text,
    occurred_at timestamptz not null
);
create index service_booking_status_history_booking on service_booking_status_history (booking_id, id);

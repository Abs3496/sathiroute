-- Additive SathiRoute partner and service booking schema. Existing shipment and route data is preserved.
create table service_partner (
    id uuid primary key,
    hub_id uuid not null references hub (id),
    code text not null unique,
    name text not null,
    email text,
    phone text,
    qualification_details text,
    active boolean not null default false,
    available boolean not null default false,
    latitude double precision check (latitude between -90 and 90),
    longitude double precision check (longitude between -180 and 180),
    location_updated_at timestamptz,
    service_radius_km double precision not null check (service_radius_km > 0),
    created_at timestamptz not null,
    check ((latitude is null) = (longitude is null))
);

create table partner_skill (
    partner_id uuid not null references service_partner (id),
    service_category text not null check (service_category in ('NURSING', 'AYURVEDIC_THERAPY', 'COUNSELLING')),
    skill_code text not null,
    qualification_verified boolean not null default false,
    primary key (partner_id, service_category, skill_code)
);

create table partner_availability (
    id uuid primary key,
    partner_id uuid not null references service_partner (id),
    day_of_week smallint not null check (day_of_week between 1 and 7),
    starts_at time not null,
    ends_at time not null,
    active boolean not null default true,
    check (ends_at > starts_at)
);
create index partner_availability_lookup on partner_availability (partner_id, day_of_week) where active;

create table service_booking (
    id uuid primary key,
    customer_subject text not null,
    customer_name text not null,
    customer_email text,
    contact_phone text not null,
    category text not null check (category in ('NURSING', 'AYURVEDIC_THERAPY', 'COUNSELLING')),
    required_skill text not null,
    appointment_start timestamptz not null,
    appointment_end timestamptz not null,
    service_duration_minutes integer not null check (service_duration_minutes > 0),
    address text not null,
    location geography(Point, 4326) not null,
    special_instructions text,
    assigned_partner_id uuid references service_partner (id),
    status text not null check (status in ('PENDING', 'SEARCHING_PARTNER', 'PARTNER_OFFERED', 'CONFIRMED',
        'PARTNER_ON_THE_WAY', 'PARTNER_ARRIVED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'REJECTED',
        'EXPIRED', 'REASSIGNMENT_REQUIRED')),
    version bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    check (appointment_end > appointment_start)
);
create index service_booking_partner_schedule on service_booking (assigned_partner_id, appointment_start, appointment_end)
    where status in ('PARTNER_OFFERED', 'CONFIRMED', 'PARTNER_ON_THE_WAY', 'PARTNER_ARRIVED', 'IN_PROGRESS');
create index service_booking_location on service_booking using gist (location);
create index service_booking_customer on service_booking (customer_subject, created_at desc);

create table allocation_attempt (
    id bigserial primary key,
    booking_id uuid not null references service_booking (id),
    partner_id uuid not null references service_partner (id),
    attempt_number integer not null check (attempt_number > 0),
    status text not null check (status in ('OFFERED', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'SUPERSEDED')),
    offered_at timestamptz not null,
    responded_at timestamptz,
    unique (booking_id, attempt_number),
    unique (booking_id, partner_id, attempt_number)
);
create index allocation_attempt_booking on allocation_attempt (booking_id, attempt_number desc);

create table partner_location_history (
    partner_id uuid not null references service_partner (id),
    recorded_at timestamptz not null,
    position geography(Point, 4326) not null,
    primary key (partner_id, recorded_at)
);
create index partner_location_history_at on partner_location_history using brin (recorded_at);

create table partner_route (
    id uuid primary key,
    partner_id uuid not null references service_partner (id),
    service_date date not null,
    status text not null check (status in ('PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    version bigint not null default 0,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    unique (partner_id, service_date)
);

create table partner_route_stop (
    route_id uuid not null references partner_route (id),
    sequence integer not null check (sequence > 0),
    booking_id uuid not null unique references service_booking (id),
    planned_arrival timestamptz not null,
    predicted_arrival timestamptz,
    arrived_at timestamptz,
    primary key (route_id, sequence) deferrable initially deferred
);

create table service_completion (
    booking_id uuid primary key references service_booking (id),
    partner_id uuid not null references service_partner (id),
    arrived_at timestamptz not null,
    started_at timestamptz,
    completed_at timestamptz,
    completion_notes text,
    otp_verified boolean not null default false,
    customer_confirmed_at timestamptz,
    check (started_at is null or started_at >= arrived_at),
    check (completed_at is null or (started_at is not null and completed_at >= started_at))
);

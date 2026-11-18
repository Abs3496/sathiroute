alter table service_completion
    add column otp_required boolean not null default false,
    add column otp_hash char(64),
    add column otp_attempts smallint not null default 0 check (otp_attempts between 0 and 5),
    add column completion_submitted_at timestamptz,
    add column version bigint not null default 0;

create table service_completion_event (
    id bigserial primary key,
    booking_id uuid not null references service_booking (id),
    event_type text not null check (event_type in (
        'ARRIVED', 'STARTED', 'COMPLETION_SUBMITTED', 'OTP_FAILED', 'CUSTOMER_CONFIRMED',
        'COMPLETED', 'EVIDENCE_ADDED', 'ADMIN_CLOSED'
    )),
    actor_subject text not null,
    occurred_at timestamptz not null,
    details text not null default ''
);
create index service_completion_event_history on service_completion_event (booking_id, id);

create table service_completion_evidence (
    id uuid primary key,
    booking_id uuid not null references service_booking (id),
    uploaded_by text not null,
    file_name text not null,
    content_type text not null check (content_type in ('image/jpeg', 'image/png', 'application/pdf')),
    sha256 char(64) not null,
    content bytea not null,
    uploaded_at timestamptz not null
);
create index service_completion_evidence_booking on service_completion_evidence (booking_id, uploaded_at);

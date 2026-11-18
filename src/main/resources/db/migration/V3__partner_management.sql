-- Partner account ownership is bound to the authenticated OIDC subject; passwords remain with the identity provider.
alter table service_partner
    add column auth_subject text,
    add column registration_status text not null default 'PENDING'
        check (registration_status in ('PENDING', 'APPROVED', 'REJECTED')),
    add column reviewed_by text,
    add column reviewed_at timestamptz;

create unique index service_partner_auth_subject on service_partner (auth_subject) where auth_subject is not null;

create table partner_time_off (
    partner_id uuid not null references service_partner (id),
    unavailable_date date not null,
    reason text,
    created_at timestamptz not null,
    primary key (partner_id, unavailable_date)
);

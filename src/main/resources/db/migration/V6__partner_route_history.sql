create table partner_route_history (
    id bigserial primary key,
    route_id uuid not null references partner_route (id),
    status text not null check (status in ('PLANNED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    version bigint not null,
    changed_at timestamptz not null,
    changed_by text not null,
    reason text not null,
    snapshot jsonb not null
);
create index partner_route_history_lookup on partner_route_history (route_id, id desc);
create index partner_route_day_status on partner_route (service_date, status);

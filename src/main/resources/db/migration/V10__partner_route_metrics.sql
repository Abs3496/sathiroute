alter table partner_route
    add column km double precision not null default 0 check (km >= 0),
    add column planned_return timestamptz;

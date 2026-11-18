-- Persist offer expiry and provide an indexed partner location for nearby allocation searches.
alter table allocation_attempt add column expires_at timestamptz;
alter table allocation_attempt add column response_reason text;
alter table service_partner add column geo_location geography(Point, 4326);
update service_partner set geo_location = ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
where latitude is not null and longitude is not null;
create index service_partner_geo_location on service_partner using gist (geo_location);
create index allocation_attempt_open_offer on allocation_attempt (expires_at) where status = 'OFFERED';

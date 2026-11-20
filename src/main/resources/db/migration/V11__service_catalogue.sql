CREATE TABLE care_service_catalogue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category TEXT NOT NULL CHECK (category IN ('NURSING', 'AYURVEDIC_THERAPY', 'COUNSELLING')),
    service_code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO care_service_catalogue (category, service_code, name, description, created_by)
VALUES
    ('NURSING', 'NURSING', 'Nursing', 'Qualified nursing visits and home care.', 'system:seed'),
    ('AYURVEDIC_THERAPY', 'AYURVEDIC_THERAPY', 'Ayurvedic Therapy', 'Ayurvedic therapy and wellness services at home.', 'system:seed'),
    ('COUNSELLING', 'COUNSELLING', 'Counselling', 'Counselling and emotional wellness support.', 'system:seed');

CREATE INDEX care_service_catalogue_category_active_idx
    ON care_service_catalogue (category, active, name);

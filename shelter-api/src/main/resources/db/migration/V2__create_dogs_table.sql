CREATE TABLE dogs (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    breed       VARCHAR(50),
    birth_year  INT,
    description TEXT,
    status      VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT dogs_status_check CHECK (status IN ('AVAILABLE', 'SPONSORED', 'ADOPTED'))
);

CREATE INDEX idx_dogs_status ON dogs (status);

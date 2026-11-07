-- Care details of a dog. They are not public: only administrators and the approved sponsor of the dog
-- can read them (the rule is enforced by the API, not by the database).
CREATE TABLE dog_care (
    dog_id           BIGINT PRIMARY KEY REFERENCES dogs (id) ON DELETE CASCADE,
    sterilized       BOOLEAN,
    microchip_number VARCHAR(30),
    health_notes     TEXT,
    -- Visible to administrators only, never to the sponsor.
    internal_note    TEXT,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE dog_vaccinations (
    id            BIGSERIAL PRIMARY KEY,
    dog_id        BIGINT NOT NULL REFERENCES dog_care (dog_id) ON DELETE CASCADE,
    name          VARCHAR(100) NOT NULL,
    vaccinated_on DATE NOT NULL,
    next_due_on   DATE,
    CONSTRAINT dog_vaccinations_dates_check CHECK (next_due_on IS NULL OR next_due_on >= vaccinated_on)
);

CREATE INDEX idx_dog_vaccinations_dog ON dog_vaccinations (dog_id);

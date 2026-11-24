CREATE TABLE dog_visits (
                            id         BIGSERIAL PRIMARY KEY,
                            dog_id     BIGINT NOT NULL REFERENCES dogs (id) ON DELETE CASCADE,
                            user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
                            visit_at   TIMESTAMP WITH TIME ZONE NOT NULL,
                            status     VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
                            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                            CONSTRAINT dog_visit_status_check CHECK (status IN ('BOOKED', 'CANCELLED'))
);

-- A time can be booked only once for a dog (it can be booked again after a cancellation).
CREATE UNIQUE INDEX one_booked_visit_per_dog_and_time
    ON dog_visits (dog_id, visit_at) WHERE status = 'BOOKED';

CREATE INDEX idx_dog_visits_user ON dog_visits (user_id);

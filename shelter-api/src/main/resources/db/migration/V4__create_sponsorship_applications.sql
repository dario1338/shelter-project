CREATE TABLE sponsorship_applications (
                                          id         BIGSERIAL PRIMARY KEY,
                                          user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
                                          dog_id     BIGINT NOT NULL REFERENCES dogs (id) ON DELETE CASCADE,
                                          status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                          user_note  TEXT,
                                          admin_note TEXT,
                                          created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                          updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
                                          CONSTRAINT sponsorship_status_check CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

-- A user can have only one pending application per dog (a new one is allowed after rejection or cancellation).
CREATE UNIQUE INDEX one_pending_application_per_user_and_dog
    ON sponsorship_applications (user_id, dog_id) WHERE status = 'PENDING';

-- A dog can have only one approved sponsor.
CREATE UNIQUE INDEX one_approved_application_per_dog
    ON sponsorship_applications (dog_id) WHERE status = 'APPROVED';

CREATE INDEX idx_sponsorship_applications_user ON sponsorship_applications (user_id);
CREATE INDEX idx_sponsorship_applications_status ON sponsorship_applications (status);
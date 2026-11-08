-- Photos of a dog. The files themselves live in object storage; the database keeps only the key of each file.
-- Photos are public, like the dogs. The first photo (lowest sort_order, then id) is the main one.
CREATE TABLE dog_images (
    id           BIGSERIAL PRIMARY KEY,
    dog_id       BIGINT       NOT NULL REFERENCES dogs (id) ON DELETE CASCADE,
    storage_key  VARCHAR(200) NOT NULL,
    content_type VARCHAR(50)  NOT NULL,
    sort_order   INTEGER      NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT dog_images_storage_key_unique UNIQUE (storage_key)
);

CREATE INDEX idx_dog_images_dog ON dog_images (dog_id, sort_order, id);

-- When the shelter approved or rejected an application. Additive only: the previous API version keeps running on this
-- schema during a blue-green switch and after a rollback, and it simply ignores the new column.
-- Short limits, for this migration's transaction only: if the table is busy, fail fast instead of blocking every
-- request queued behind the ALTER TABLE lock.
SET LOCAL lock_timeout = '1s';
SET LOCAL statement_timeout = '5s';

ALTER TABLE sponsorship_applications ADD COLUMN decided_at TIMESTAMP WITH TIME ZONE;

-- Applications decided before this column existed: the decision was their last change, so updated_at is the moment.
UPDATE sponsorship_applications SET decided_at = updated_at WHERE status IN ('APPROVED', 'REJECTED');

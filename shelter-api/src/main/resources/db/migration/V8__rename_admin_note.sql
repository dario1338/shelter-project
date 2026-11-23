-- Controlled experiment for the migration lint (never merged): renaming a column breaks the API instance that still
-- runs the previous version during a blue-green switch, and the previous version after a rollback.
ALTER TABLE sponsorship_applications RENAME COLUMN admin_note TO decision_note;

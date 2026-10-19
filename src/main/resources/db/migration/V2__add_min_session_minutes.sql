-- Minimum useful session length for tasks worked on over several sittings (e.g. reading a book).
-- Nullable: existing rows keep NULL, which preserves their current recommendation behaviour.
ALTER TABLE tasks
    ADD COLUMN min_session_minutes INTEGER;

ALTER TABLE tasks
    ADD CONSTRAINT chk_tasks_min_session
        CHECK (min_session_minutes IS NULL OR min_session_minutes > 0);

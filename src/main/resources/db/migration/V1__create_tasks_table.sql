CREATE TABLE tasks (
    id                          UUID NOT NULL,
    title                       VARCHAR(255) NOT NULL,
    description                 TEXT,

    status                      VARCHAR(20) NOT NULL,
    importance                  SMALLINT NOT NULL DEFAULT 3,
    estimated_duration_minutes  INTEGER,

    deadline                    DATE,
    scheduled_for               TIMESTAMPTZ,

    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL,
    completed_at                TIMESTAMPTZ,

    version                     BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_tasks PRIMARY KEY (id),

    CONSTRAINT chk_tasks_status
        CHECK (status IN ('SCHEDULED', 'ACTIVE', 'COMPLETED')),

    CONSTRAINT chk_tasks_importance
        CHECK (importance BETWEEN 1 AND 5),

    CONSTRAINT chk_tasks_estimated_duration
        CHECK (estimated_duration_minutes IS NULL OR estimated_duration_minutes > 0),

    CONSTRAINT chk_tasks_scheduled_requires_date
        CHECK (status <> 'SCHEDULED' OR scheduled_for IS NOT NULL),

    CONSTRAINT chk_tasks_completed_requires_timestamp
        CHECK (
            (status = 'COMPLETED' AND completed_at IS NOT NULL)
            OR (status <> 'COMPLETED' AND completed_at IS NULL)
        )
);

CREATE INDEX idx_tasks_status_scheduled_for ON tasks (status, scheduled_for);

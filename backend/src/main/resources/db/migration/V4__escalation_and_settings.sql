-- ==============================================================================
-- Flyway V4: escalation level and administrator settings
-- ==============================================================================

-- How far up the escalation chain an incident has gone:
-- 0 = not escalated, 1 = team lead, 2 = administrator / IT manager.
ALTER TABLE incidents ADD COLUMN escalation_level INTEGER NOT NULL DEFAULT 0;
ALTER TABLE incidents ADD CONSTRAINT ck_incidents_escalation CHECK (escalation_level BETWEEN 0 AND 10);

-- Small key/value store for runtime configuration chosen by administrators,
-- e.g. which assignment and priority strategies are active. Survives restarts.
CREATE TABLE app_settings (
    setting_key   VARCHAR(60)  PRIMARY KEY,
    setting_value VARCHAR(200) NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by    VARCHAR(20)  NOT NULL
);

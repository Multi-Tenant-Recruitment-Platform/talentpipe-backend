-- ---------------------------------------------------------------------------
-- V22: Blind mode is part of a pipeline template.
--
-- Product decision: a standard hiring workflow can enforce bias-free
-- screening, so blind mode is set on the template, and a vacancy created from
-- it starts with the template's setting (V23 snapshots it onto the vacancy).
--
-- Existing templates keep today's behaviour: the column is NOT NULL with a
-- default, so every row already in the table reads false.
-- ---------------------------------------------------------------------------

ALTER TABLE pipeline_templates
    ADD COLUMN blind_mode_enabled BOOLEAN NOT NULL DEFAULT false;

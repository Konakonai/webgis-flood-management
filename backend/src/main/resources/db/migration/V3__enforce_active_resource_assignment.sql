-- A resource may back at most one actively processing work order.
-- Application row locks provide the friendly conflict; this partial unique index
-- is the final database-level guard across every writer and application instance.
CREATE UNIQUE INDEX uq_work_order_processing_resource
    ON work_order(assigned_resource_id)
    WHERE assigned_resource_id IS NOT NULL AND status = 'PROCESSING';

-- Nearby public-report queries cast points to geography for metre-based distance.
CREATE INDEX idx_work_order_unresolved_report_geography
    ON work_order USING GIST ((geom::geography))
    WHERE type = 'REPORT' AND status IN ('PENDING', 'PROCESSING');

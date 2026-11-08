-- A transfer log is written in the same transaction as the outcome of the transfer,
-- so it is only ever stored as SUCCESS or FAILED: RECEIVED was never visible.
ALTER TABLE transfer_logs
    DROP CONSTRAINT transfer_logs_status_check,
    ADD CONSTRAINT transfer_logs_status_check CHECK (status IN ('FAILED', 'SUCCESS'));

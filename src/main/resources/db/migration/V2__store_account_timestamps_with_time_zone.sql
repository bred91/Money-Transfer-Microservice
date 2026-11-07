-- Store the account timestamps as absolute instants (TIMESTAMP WITH TIME ZONE), like transfer_logs.created_at.
-- The existing values were written by the application running in the Docker image, whose JVM uses UTC:
-- they are converted explicitly from UTC, regardless of the time zone of the session running the migration.
ALTER TABLE accounts
    ALTER COLUMN created_at TYPE TIMESTAMP(6) WITH TIME ZONE USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN last_modified_at TYPE TIMESTAMP(6) WITH TIME ZONE USING last_modified_at AT TIME ZONE 'UTC';

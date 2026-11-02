-- Optional sample accounts for manual testing (not a migration). uuidv7() requires PostgreSQL 18+.
-- Usage: docker compose exec -T postgres psql -U my_user -d my_database < scripts/sample-data.sql
INSERT INTO accounts (id, balance, created_at, last_modified_at, owner_name, owner_surname, status)
VALUES
    (uuidv7(), 1000.00, NOW(), NOW(), 'John', 'Doe', 'ACTIVE'),
    (uuidv7(), 5000.50, NOW(), NOW(), 'Jane', 'Smith', 'ACTIVE'),
    (uuidv7(), 2500.75, NOW(), NOW(), 'Bob', 'Johnson', 'ACTIVE');

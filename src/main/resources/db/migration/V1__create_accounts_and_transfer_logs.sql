CREATE TABLE accounts
(
    id               UUID           NOT NULL,
    owner_name       VARCHAR(255)   NOT NULL,
    owner_surname    VARCHAR(255)   NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    balance          NUMERIC(19, 2) NOT NULL,
    created_at       TIMESTAMP(6)   NOT NULL,
    last_modified_at TIMESTAMP(6)   NOT NULL,
    CONSTRAINT accounts_pk PRIMARY KEY (id),
    CONSTRAINT accounts_status_check CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT accounts_balance_non_negative_check CHECK (balance >= 0)
);

-- No foreign keys to accounts: failed transfers are logged even when an account does not exist.
CREATE TABLE transfer_logs
(
    id              UUID                        NOT NULL,
    transaction_id  VARCHAR(255)                NOT NULL,
    from_account_id UUID                        NOT NULL,
    to_account_id   UUID                        NOT NULL,
    amount          NUMERIC(19, 2)              NOT NULL,
    status          VARCHAR(20)                 NOT NULL,
    failed_reason   VARCHAR(255),
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT transfer_logs_pk PRIMARY KEY (id),
    CONSTRAINT transfer_logs_transaction_id_uk UNIQUE (transaction_id),
    CONSTRAINT transfer_logs_status_check CHECK (status IN ('RECEIVED', 'FAILED', 'SUCCESS'))
);

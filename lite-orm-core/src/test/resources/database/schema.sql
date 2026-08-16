DROP TABLE IF EXISTS compatibility_users;

CREATE TABLE compatibility_users (
    id ${identity},
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL,
    business_date DATE NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    payload ${binary} NOT NULL
);

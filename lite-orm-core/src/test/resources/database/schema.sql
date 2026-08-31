DROP TABLE IF EXISTS compatibility_users;

CREATE TABLE compatibility_users (
    id ${identity},
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL,
    business_date DATE NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    uuid_value ${uuid} NOT NULL,
    local_time_value ${localTime} NOT NULL,
    offset_date_time_value ${offsetDateTime} NOT NULL,
    payload ${binary} NOT NULL
);

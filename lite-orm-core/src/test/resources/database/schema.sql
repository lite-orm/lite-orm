DROP TABLE IF EXISTS compatibility_users;
DROP TABLE IF EXISTS standard_type_values;

CREATE TABLE compatibility_users (
    id ${identity},
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL,
    business_date DATE NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    uuid_value ${uuid},
    local_time_value ${localTime},
    offset_date_time_value ${offsetDateTime},
    payload ${binary} NOT NULL
);

CREATE TABLE standard_type_values (
    id BIGINT PRIMARY KEY,
    integer_value DECIMAL(38, 0),
    binary_value ${binary},
    util_date_value TIMESTAMP(6),
    util_date_only_value DATE,
    util_time_only_value TIME(6),
    sql_date_value DATE,
    sql_time_value TIME(6),
    sql_timestamp_value TIMESTAMP(6),
    year_value INTEGER,
    month_value INTEGER,
    year_month_value VARCHAR(7),
    japanese_date_value DATE,
    enum_name_value VARCHAR(32),
    enum_ordinal_value INTEGER,
    national_char_value ${nationalChar},
    national_varchar_value ${nationalVarchar}
);

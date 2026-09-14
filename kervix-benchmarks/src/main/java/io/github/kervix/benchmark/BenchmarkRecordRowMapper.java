package io.github.kervix.benchmark;

import io.github.kervix.api.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public final class BenchmarkRecordRowMapper implements RowMapper<BenchmarkRecord> {

    public BenchmarkRecordRowMapper() {
    }

    @Override
    public BenchmarkRecord map(ResultSet resultSet) throws SQLException {
        return new BenchmarkRecord(
            resultSet.getLong(1),
            resultSet.getString(2),
            resultSet.getInt(3)
        );
    }
}

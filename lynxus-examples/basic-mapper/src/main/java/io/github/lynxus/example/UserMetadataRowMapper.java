package io.github.lynxus.example;

import io.github.lynxus.api.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class UserMetadataRowMapper implements RowMapper<UserMetadata> {

    public UserMetadataRowMapper() {
    }

    @Override
    public UserMetadata map(ResultSet resultSet) throws SQLException {
        return new UserMetadata(resultSet.getLong(1), new JsonValue(resultSet.getString(2)));
    }
}

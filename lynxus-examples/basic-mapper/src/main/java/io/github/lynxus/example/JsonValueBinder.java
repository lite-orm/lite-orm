package io.github.lynxus.example;

import io.github.lynxus.api.ParameterBinder;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

public class JsonValueBinder implements ParameterBinder<JsonValue> {

    public JsonValueBinder() {
    }

    @Override
    public void bind(PreparedStatement statement, int index, JsonValue value) throws SQLException {
        if (value == null || value.value() == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value.value());
        }
    }
}

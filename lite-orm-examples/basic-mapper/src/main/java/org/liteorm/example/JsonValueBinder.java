package org.liteorm.example;

import org.liteorm.api.ParameterBinder;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class JsonValueBinder implements ParameterBinder<JsonValue> {

    public JsonValueBinder() {
    }

    @Override
    public void bind(PreparedStatement statement, int index, JsonValue value) throws SQLException {
        statement.setString(index, value.value());
    }
}

package io.github.kervix.example;

import io.github.kervix.api.ParameterBinder;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public class UserSearchPrefixBinder implements ParameterBinder<String> {

    public UserSearchPrefixBinder() {
    }

    @Override
    public void bind(PreparedStatement statement, int index, String value) throws SQLException {
        statement.setString(index, value + "%");
    }
}

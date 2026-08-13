package org.liteorm.example;

import org.liteorm.api.BoundParameter;
import org.liteorm.api.BoundSql;
import org.liteorm.api.SqlProvider;

import java.util.ArrayList;
import java.util.List;

public class UserSearchProvider implements SqlProvider<UserSearch> {

    public UserSearchProvider() {
    }

    @Override
    public BoundSql provide(UserSearch search) {
        StringBuilder sql = new StringBuilder("SELECT id, name, email, age FROM users");
        List<BoundParameter> parameters = new ArrayList<>();
        if (search.namePrefix() != null && !search.namePrefix().isBlank()) {
            sql.append(" WHERE name LIKE ?");
            parameters.add(BoundParameter.of(search.namePrefix() + "%"));
        }
        sql.append(search.descending() ? " ORDER BY id DESC" : " ORDER BY id ASC");
        return new BoundSql(sql.toString(), parameters);
    }
}

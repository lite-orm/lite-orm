package io.github.lynxus.example;

import io.github.lynxus.api.BoundParameter;
import io.github.lynxus.api.BoundSql;
import io.github.lynxus.api.SqlProvider;

import java.util.ArrayList;
import java.util.List;

public class UserSearchProvider implements SqlProvider<UserSearch> {

    private final UserSearchPrefixBinder namePrefixBinder = new UserSearchPrefixBinder();

    public UserSearchProvider() {
    }

    @Override
    public BoundSql provide(UserSearch search) {
        StringBuilder sql = new StringBuilder("SELECT id, name, email, age FROM users");
        List<BoundParameter<?>> parameters = new ArrayList<>();
        if (search.namePrefix() != null && !search.namePrefix().isBlank()) {
            sql.append(" WHERE name LIKE ?");
            parameters.add(BoundParameter.bound(search.namePrefix(), namePrefixBinder));
        }
        sql.append(search.descending() ? " ORDER BY id DESC" : " ORDER BY id ASC");
        return new BoundSql(sql.toString(), parameters);
    }
}

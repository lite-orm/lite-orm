package io.github.lynxus.benchmark;

import java.util.Map;

public final class MyBatisSqlProvider {

    private MyBatisSqlProvider() {
    }

    public static String dynamic(Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder("SELECT id, name, age FROM benchmark_users WHERE 1 = 1");
        if (parameters.get("name") != null) {
            sql.append(" AND name = #{name}");
        }
        if (parameters.get("minimumAge") != null) {
            sql.append(" AND age >= #{minimumAge}");
        }
        return sql.toString();
    }
}

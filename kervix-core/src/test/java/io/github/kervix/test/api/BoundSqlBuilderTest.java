package io.github.kervix.test.api;

import org.junit.jupiter.api.Test;
import io.github.kervix.api.BoundSql;
import io.github.kervix.api.BoundSqlBuilder;
import io.github.kervix.api.ConfigurationException;
import io.github.kervix.api.ParameterBinder;

import java.sql.JDBCType;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundSqlBuilderTest {

    @Test
    void buildsSpacedSqlWithAtomicallyAlignedParameters() {
        BoundSqlBuilder builder = BoundSqlBuilder.create("example.UserMapper.find");

        builder.append("SELECT id, name");
        builder.append("FROM users");
        builder.append("WHERE id =");
        builder.parameter(7L, null, Long.class, JDBCType.BIGINT);

        BoundSql boundSql = builder.build();

        assertEquals("SELECT id, name FROM users WHERE id = ?", boundSql.sql());
        assertArrayEquals(new Object[]{7L}, boundSql.parameterValues());
        assertArrayEquals(new Class<?>[]{Long.class}, boundSql.parameterTypes());
        assertArrayEquals(new JDBCType[]{JDBCType.BIGINT}, boundSql.parameterJdbcTypes());
    }

    @Test
    void appendsOnlyNonEmptyNormalizedWhereClausesWithTheirParameters() {
        BoundSqlBuilder sql = BoundSqlBuilder.create("example.UserMapper.find");
        sql.append("SELECT id FROM users");

        BoundSqlBuilder emptyWhere = sql.fragment();
        sql.where(emptyWhere);

        BoundSqlBuilder where = sql.fragment();
        where.append("AND name =");
        where.parameter("Alice", null, String.class, JDBCType.VARCHAR);
        sql.where(where);

        BoundSql boundSql = sql.build();

        assertEquals("SELECT id FROM users WHERE name = ?", boundSql.sql());
        assertArrayEquals(new Object[]{"Alice"}, boundSql.parameterValues());
    }

    @Test
    void normalizesSetClausesAndRejectsMissingAssignmentsWithStatementContext() {
        BoundSqlBuilder sql = BoundSqlBuilder.create("example.UserMapper.update");
        sql.append("UPDATE users");
        BoundSqlBuilder set = sql.fragment();
        set.append("name =");
        set.parameter("Alice", null, String.class, JDBCType.VARCHAR);
        set.append(",");

        sql.set(set);

        assertEquals("UPDATE users SET name = ?", sql.build().sql());
        var failure = assertThrows(
            io.github.kervix.api.ConfigurationException.class,
            () -> BoundSqlBuilder.create("example.UserMapper.emptyUpdate")
                .set(BoundSqlBuilder.create("example.UserMapper.emptyUpdate").fragment()));
        assertEquals("example.UserMapper.emptyUpdate", failure.getStatementId());
    }

    @Test
    void trimsConfiguredPrefixAndSuffixOverridesWithoutLosingParameters() {
        BoundSqlBuilder sql = BoundSqlBuilder.create("example.UserMapper.find");
        sql.append("SELECT id FROM users");
        BoundSqlBuilder clause = sql.fragment();
        clause.append("OR id =");
        clause.parameter(7L, null, Long.class, JDBCType.BIGINT);
        clause.append(",");

        sql.trim(clause, "WHERE", null, "AND|OR", ",");

        BoundSql boundSql = sql.build();
        assertEquals("SELECT id FROM users WHERE id = ?", boundSql.sql());
        assertArrayEquals(new Object[]{7L}, boundSql.parameterValues());
    }

    @Test
    void reportsBlankDynamicSqlWithStatementContext() {
        var failure = assertThrows(
            io.github.kervix.api.ConfigurationException.class,
            () -> BoundSqlBuilder.create("example.UserMapper.find").build());

        assertEquals("Dynamic SQL produced blank SQL [phase=CONFIGURATION, "
            + "statementId=example.UserMapper.find]", failure.getMessage());
    }

    @Test
    void preservesDirectBindingForValuesWithoutCompileTimeTypes() {
        BoundSql boundSql = BoundSqlBuilder.create("example.UserMapper.find")
            .append("SELECT")
            .parameter("value", null, null, null)
            .build();

        assertArrayEquals(new Class<?>[]{null}, boundSql.parameterTypes());
        assertNotNull(boundSql.parameterBinders()[0]);
    }

    @Test
    void preservesNullableValuesForCustomBinders() {
        ParameterBinder<String> binder =
            (statement, index, value) -> statement.setString(index, value);

        BoundSql boundSql = BoundSqlBuilder.create("example.UserMapper.find")
            .append("SELECT")
            .parameter(null, binder, String.class, JDBCType.VARCHAR)
            .build();

        assertArrayEquals(new Object[]{null}, boundSql.parameterValues());
        assertSame(binder, boundSql.parameterBinders()[0]);
    }

    @Test
    void preservesQuestionMarksInSqlTextWithoutChangingGeneratedParameterRouting() {
        BoundSql boundSql = BoundSqlBuilder.create("example.UserMapper.find")
            .append("SELECT '?' AS marker, payload ? 'enabled', payload ?? 'escaped'")
            .append("FROM documents WHERE id =")
            .parameter(7L, null, Long.class, JDBCType.BIGINT)
            .build();

        assertEquals("SELECT '?' AS marker, payload ? 'enabled', payload ?? 'escaped' "
            + "FROM documents WHERE id = ?", boundSql.sql());
        assertArrayEquals(new Object[]{7L}, boundSql.parameterValues());
    }

    @Test
    void preservesInternalMarkerCharactersInSqlText() {
        String marker = "\uE000";

        BoundSql boundSql = BoundSqlBuilder.create("example.UserMapper.find")
            .append("SELECT '" + marker + "' AS marker WHERE id =")
            .parameter(7L, null, Long.class, JDBCType.BIGINT)
            .build();

        assertEquals("SELECT '" + marker + "' AS marker WHERE id = ?", boundSql.sql());
        assertArrayEquals(new Object[]{7L}, boundSql.parameterValues());
    }

    @Test
    void trimOverridesCannotRemoveGeneratedParameterMarkers() {
        BoundSqlBuilder sql = BoundSqlBuilder.create("example.UserMapper.find");
        sql.append("SELECT 1");
        BoundSqlBuilder clause = sql.fragment().parameter(7L, null, Long.class, JDBCType.BIGINT);

        sql.trim(clause, "WHERE", null, "?", "P");

        BoundSql boundSql = sql.build();
        assertEquals("SELECT 1 WHERE ?", boundSql.sql());
        assertArrayEquals(new Object[]{7L}, boundSql.parameterValues());
    }

    @Test
    void trimOverridesCannotSplitEscapedSqlTextMarkers() {
        String marker = "\uE000";
        BoundSqlBuilder sql = BoundSqlBuilder.create("example.UserMapper.find");
        BoundSqlBuilder clause = sql.fragment().append("-- " + marker);

        sql.trim(clause, null, null, null, "L");

        assertEquals("-- " + marker, sql.build().sql());
    }

    @Test
    void rejectsNullAndSelfCompositionWithStatementContext() {
        BoundSqlBuilder sql = BoundSqlBuilder.create("example.UserMapper.find");

        ConfigurationException nullFailure = assertThrows(
            ConfigurationException.class,
            () -> sql.where(null));
        ConfigurationException selfFailure = assertThrows(
            ConfigurationException.class,
            () -> sql.set(sql));

        assertEquals("example.UserMapper.find", nullFailure.getStatementId());
        assertEquals("example.UserMapper.find", selfFailure.getStatementId());
    }

    @Test
    void rejectsFragmentsFromAnotherStatementWithOwningStatementContext() {
        BoundSqlBuilder sql = BoundSqlBuilder.create("example.UserMapper.find");
        BoundSqlBuilder other = BoundSqlBuilder.create("example.UserMapper.update").fragment();

        ConfigurationException failure = assertThrows(
            ConfigurationException.class,
            () -> sql.where(other));

        assertEquals("example.UserMapper.find", failure.getStatementId());
    }
}

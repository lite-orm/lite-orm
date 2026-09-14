package io.github.lynxus.test.provider;

import org.junit.jupiter.api.Test;
import io.github.lynxus.api.BoundParameter;
import io.github.lynxus.api.BoundSql;
import io.github.lynxus.api.ConfigurationException;
import io.github.lynxus.api.ParameterBinder;

import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundSqlTest {

    @Test
    void createsBoundSqlFromAlignedGeneratedParameterArrays() {
        BoundSql boundSql = BoundSql.of(
            "SELECT id FROM users WHERE id = ?",
            new Object[]{1L},
            new ParameterBinder<?>[]{null},
            new Class<?>[]{Long.class},
            new JDBCType[]{JDBCType.BIGINT});

        assertArrayEquals(new Object[]{1L}, boundSql.parameterValues());
        assertArrayEquals(new Class<?>[]{Long.class}, boundSql.parameterTypes());
        assertArrayEquals(new JDBCType[]{JDBCType.BIGINT}, boundSql.parameterJdbcTypes());
    }

    @Test
    void rejectsMisalignedGeneratedParameterArrays() {
        assertThrows(ConfigurationException.class, () -> BoundSql.of(
            "SELECT ?",
            new Object[]{1L},
            null,
            new Class<?>[0],
            null));
    }

    @Test
    void preservesDirectBindingForGeneratedParametersWithoutStaticTypes() {
        BoundSql boundSql = BoundSql.of(
            "SELECT ?",
            new Object[]{"value"},
            new ParameterBinder<?>[]{null},
            new Class<?>[]{null},
            new JDBCType[]{null});

        assertArrayEquals(new Class<?>[]{null}, boundSql.parameterTypes());
        assertEquals(1, boundSql.parameterBinders().length);
        assertNotNull(boundSql.parameterBinders()[0]);
    }

    @Test
    void preservesOrderedParametersAndDefensivelyCopiesList() {
        List<BoundParameter<?>> parameters = new ArrayList<>();
        parameters.add(BoundParameter.of(Long.class, 1L));
        BoundSql boundSql = new BoundSql("SELECT ?", parameters);
        parameters.add(BoundParameter.of(Long.class, 2L));

        assertEquals(1, boundSql.parameters().size());
        assertArrayEquals(new Object[]{1L}, boundSql.parameterValues());
    }

    @Test
    void exposesBinderSlotsAlignedWithProviderParameterOrder() {
        ParameterBinder<String> binder = (statement, index, value) -> statement.setString(index, value);
        BoundSql boundSql = new BoundSql("SELECT ?, ?", List.of(
            BoundParameter.bound("custom", binder),
            BoundParameter.of(Long.class, 2L)
        ));

        assertArrayEquals(new Object[]{"custom", 2L}, boundSql.parameterValues());
        assertArrayEquals(new ParameterBinder<?>[]{binder, null}, boundSql.parameterBinders());
    }

    @Test
    void preservesProviderParameterTypesForRuntimeRoutingIncludingNulls() {
        BoundSql boundSql = new BoundSql("SELECT ?, ?", List.of(
            BoundParameter.of(String.class, null, JDBCType.CLOB),
            BoundParameter.of(Number.class, 2L)
        ));

        assertArrayEquals(new Class<?>[]{String.class, Number.class}, boundSql.parameterTypes());
        assertArrayEquals(new JDBCType[]{JDBCType.CLOB, null}, boundSql.parameterJdbcTypes());
    }

    @Test
    void rejectsDirectProviderParametersWithoutAnExplicitBinder() {
        assertThrows(NullPointerException.class, () -> new BoundParameter<>("value", null));
    }

    @Test
    void rejectsInvalidProviderResultsBeforeJdbcPreparation() {
        assertThrows(ConfigurationException.class, () -> BoundSql.requireValid(null, "Mapper#method"));
        assertThrows(ConfigurationException.class, () -> new BoundSql(" ", List.of()));
        assertThrows(ConfigurationException.class, () -> new BoundSql("SELECT 1", null));
    }
}

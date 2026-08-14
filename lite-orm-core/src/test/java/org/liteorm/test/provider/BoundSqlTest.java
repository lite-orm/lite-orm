package org.liteorm.test.provider;

import org.junit.jupiter.api.Test;
import org.liteorm.api.BoundParameter;
import org.liteorm.api.BoundSql;
import org.liteorm.api.ConfigurationException;
import org.liteorm.api.ParameterBinder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundSqlTest {

    @Test
    void preservesOrderedParametersAndDefensivelyCopiesList() {
        List<BoundParameter<?>> parameters = new ArrayList<>();
        parameters.add(BoundParameter.of(1L));
        BoundSql boundSql = new BoundSql("SELECT ?", parameters);
        parameters.add(BoundParameter.of(2L));

        assertEquals(1, boundSql.parameters().size());
        assertArrayEquals(new Object[]{1L}, boundSql.parameterValues());
    }

    @Test
    void exposesBinderSlotsAlignedWithProviderParameterOrder() {
        ParameterBinder<String> binder = (statement, index, value) -> statement.setString(index, value);
        BoundSql boundSql = new BoundSql("SELECT ?, ?", List.of(
            BoundParameter.bound("custom", binder),
            BoundParameter.of(2L)
        ));

        assertArrayEquals(new Object[]{"custom", 2L}, boundSql.parameterValues());
        assertArrayEquals(new ParameterBinder<?>[]{binder, null}, boundSql.parameterBinders());
    }

    @Test
    void rejectsInvalidProviderResultsBeforeJdbcPreparation() {
        assertThrows(ConfigurationException.class, () -> BoundSql.requireValid(null, "Mapper#method"));
        assertThrows(ConfigurationException.class, () -> new BoundSql(" ", List.of()));
        assertThrows(ConfigurationException.class, () -> new BoundSql("SELECT 1", null));
    }
}

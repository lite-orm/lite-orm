package org.liteorm.test.api;

import org.junit.jupiter.api.Test;
import org.liteorm.api.ConfigurationException;
import org.liteorm.api.ExecutionPhase;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.JdbcExecutionState;
import org.liteorm.api.LiteOrmException;
import org.liteorm.api.MappingException;
import org.liteorm.api.NonUniqueResultException;
import org.liteorm.api.SqlExecutionException;
import org.liteorm.api.TransactionException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExceptionContractTest {

    @Test
    void everyPublicRuntimeFailureUsesLiteOrmHierarchyAndPhase() {
        assertTrue(LiteOrmException.class.isAssignableFrom(ConfigurationException.class));
        assertTrue(LiteOrmException.class.isAssignableFrom(MappingException.class));
        assertTrue(LiteOrmException.class.isAssignableFrom(NonUniqueResultException.class));
        assertTrue(LiteOrmException.class.isAssignableFrom(SqlExecutionException.class));
        assertTrue(LiteOrmException.class.isAssignableFrom(TransactionException.class));

        assertEquals(ExecutionPhase.CONFIGURATION, new ConfigurationException("bad config").getPhase());
        NonUniqueResultException nonUnique = new NonUniqueResultException("test.Mapper.find", 2);
        assertEquals(ExecutionPhase.MAPPING, nonUnique.getPhase());
        assertTrue(nonUnique.getMessage().contains("phase=MAPPING"));
        for (TransactionException.Type type : TransactionException.Type.values()) {
            TransactionException transaction = new TransactionException(type, "transaction failed");
            assertEquals(ExecutionPhase.TRANSACTION, transaction.getPhase());
            assertTrue(transaction.getMessage().contains("phase=TRANSACTION"));
            assertTrue(transaction.getMessage().contains("type=" + type));
        }
    }

    @Test
    void sqlExecutionMessageIsRedactedButDiagnosticsAreExplicit() {
        String secret = "s3cr3t-password";
        String credentialUrl = "jdbc:mysql://admin:secret@db.internal/app";
        String sql = "SELECT * FROM users WHERE password = '" + secret
            + "' AND source_url = '" + credentialUrl + "' AND id = ?";
        ExecutionPlan plan = new ExecutionPlan(
            "test.Mapper.findSecret", sql, new Object[]{secret},
            ExecutionPlan.StatementType.SELECT, ExecutionPlan.SqlSource.XML);
        IllegalStateException cause = new IllegalStateException("driver failed");

        SqlExecutionException failure = new SqlExecutionException(
            plan, ExecutionPhase.EXECUTION, JdbcExecutionState.OUTCOME_UNKNOWN, cause);

        assertTrue(failure.getMessage().contains("statementId=test.Mapper.findSecret"));
        assertTrue(failure.getMessage().contains("phase=EXECUTION"));
        assertFalse(failure.getMessage().contains(sql));
        assertFalse(failure.getMessage().contains(secret));
        assertFalse(failure.getMessage().contains(credentialUrl));
        assertSame(cause, failure.getCause());
        assertEquals(sql, failure.diagnostics().sql());
        assertEquals(ExecutionPlan.SqlSource.XML, failure.diagnostics().sourceType());
    }

    @Test
    void mappingExceptionStoresLocationInsteadOfRowValues() {
        IllegalArgumentException cause = new IllegalArgumentException("bad value");
        MappingException failure = new MappingException(
            "Cannot map result value", "test.Mapper.find", String.class, "user_name", 1, cause);

        assertEquals(ExecutionPhase.MAPPING, failure.getPhase());
        assertEquals("test.Mapper.find", failure.getStatementId());
        assertEquals(String.class, failure.getTargetType());
        assertEquals("user_name", failure.getColumnLabel());
        assertEquals(1, failure.getColumnIndex());
        assertSame(cause, failure.getCause());
        assertFalse(failure.getMessage().contains("password"));
        Set<String> methodNames = Arrays.stream(MappingException.class.getDeclaredMethods())
            .map(method -> method.getName())
            .collect(Collectors.toSet());
        assertFalse(methodNames.contains("getRow"));
        assertFalse(Arrays.stream(MappingException.class.getDeclaredFields())
            .map(Field::getType)
            .anyMatch(Class::isArray));
    }

    @Test
    void configurationExceptionDoesNotRetainOrPrintSensitiveValues() {
        ConfigurationException failure = ConfigurationException.forConfigKey(
            "Invalid datasource URL", "dataSource.url");
        ConfigurationException statementFailure = ConfigurationException.forStatement(
            "SQL provider returned invalid output", "test.Mapper.find");

        assertEquals(ExecutionPhase.CONFIGURATION, failure.getPhase());
        assertEquals("dataSource.url", failure.getConfigKey());
        assertNull(failure.getStatementId());
        assertEquals("test.Mapper.find", statementFailure.getStatementId());
        assertTrue(statementFailure.getMessage().contains("statementId=test.Mapper.find"));
        Set<String> methodNames = Arrays.stream(ConfigurationException.class.getDeclaredMethods())
            .map(method -> method.getName())
            .collect(Collectors.toSet());
        assertFalse(methodNames.contains("getConfigValue"));
        assertEquals(Set.of("configKey", "statementId"),
            Arrays.stream(ConfigurationException.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet()));
        assertFalse(Arrays.stream(ConfigurationException.class.getDeclaredConstructors())
            .map(Constructor::getParameterTypes)
            .anyMatch(parameterTypes -> Arrays.equals(
                parameterTypes, new Class<?>[]{String.class, String.class, String.class})));
    }
}

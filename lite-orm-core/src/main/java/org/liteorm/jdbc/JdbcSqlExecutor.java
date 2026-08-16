package org.liteorm.jdbc;

import org.liteorm.api.BatchExecutionPlan;
import org.liteorm.api.ConnectionHandle;
import org.liteorm.api.ConnectionHandleFactory;
import org.liteorm.api.ExecutionInterceptor;
import org.liteorm.api.ExecutionOutcome;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.JdbcExecutionState;
import org.liteorm.api.ParameterBinder;
import org.liteorm.api.RowMapper;
import org.liteorm.api.SqlExecutionException;
import org.liteorm.api.SqlExecutor;
import org.liteorm.api.SqlResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Executes immutable plans through one fixed, non-configurable JDBC lifecycle.
 */
public final class JdbcSqlExecutor implements SqlExecutor {

    private final ConnectionHandleFactory connectionHandleFactory;
    private final List<ExecutionInterceptor> interceptors;

    public JdbcSqlExecutor(ConnectionHandleFactory connectionHandleFactory) {
        this(connectionHandleFactory, List.of());
    }

    public JdbcSqlExecutor(ConnectionHandleFactory connectionHandleFactory, List<ExecutionInterceptor> interceptors) {
        this.connectionHandleFactory = Objects.requireNonNull(connectionHandleFactory, "connectionHandleFactory");
        this.interceptors = List.copyOf(Objects.requireNonNull(interceptors, "interceptors"));
    }

    @Override
    public SqlResult execute(ExecutionPlan plan) {
        validate(plan);
        long startedAt = System.nanoTime();
        List<ExecutionInterceptor> entered = new ArrayList<>(interceptors.size());
        ConnectionHandle connectionHandle = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        SqlResult result = null;
        JdbcExecutionState executionState = JdbcExecutionState.NOT_EXECUTED;
        Throwable primaryFailure = null;

        try {
            invokeBefore(plan, entered);
            connectionHandle = Objects.requireNonNull(
                connectionHandleFactory.openHandle(), "connectionHandleFactory returned null");
            Connection connection = Objects.requireNonNull(
                connectionHandle.connection(), "connectionHandle returned null connection");
            statement = prepare(connection, plan);
            switch (plan.getStatementType()) {
                case SELECT -> {
                    bind(statement, plan.getParameters(), plan.getParameterBinders());
                    executionState = JdbcExecutionState.OUTCOME_UNKNOWN;
                    resultSet = statement.executeQuery();
                    executionState = JdbcExecutionState.EXECUTED;
                    result = SqlResult.forQuery(readRows(resultSet, plan.getRowMapper()));
                }
                case INSERT, UPDATE, DELETE -> {
                    bind(statement, plan.getParameters(), plan.getParameterBinders());
                    executionState = JdbcExecutionState.OUTCOME_UNKNOWN;
                    int updateCount = statement.executeUpdate();
                    executionState = JdbcExecutionState.EXECUTED;
                    if (plan.returnsGeneratedKey()) {
                        resultSet = statement.getGeneratedKeys();
                        result = SqlResult.forGeneratedKey(updateCount, readGeneratedKey(resultSet));
                    } else {
                        result = SqlResult.forUpdate(updateCount);
                    }
                }
                case BATCH -> {
                    BatchExecutionPlan batchPlan = (BatchExecutionPlan) plan;
                    addBatch(statement, batchPlan);
                    executionState = JdbcExecutionState.OUTCOME_UNKNOWN;
                    int[] updateCounts = batchPlan.getBatchParameters().isEmpty()
                        ? new int[0]
                        : statement.executeBatch();
                    executionState = JdbcExecutionState.EXECUTED;
                    result = SqlResult.forBatch(updateCounts);
                }
            }
            ExecutionOutcome outcome = ExecutionOutcome.success(
                plan, executionState, elapsed(startedAt), affectedRows(result), resultCount(result));
            invokeSuccess(entered, outcome);
            return result;
        } catch (Throwable failure) {
            primaryFailure = failure;
            ExecutionOutcome outcome = ExecutionOutcome.failure(
                plan, executionState, elapsed(startedAt), affectedRows(result), resultCount(result), failure);
            invokeFailure(entered, outcome, failure);
            if (failure instanceof Error error) {
                throw error;
            }
            throw new SqlExecutionException(plan, executionState, failure);
        } finally {
            Throwable cleanupFailure = closeResources(resultSet, statement, connectionHandle);
            if (cleanupFailure != null) {
                if (primaryFailure != null) {
                    appendFlattened(primaryFailure, cleanupFailure);
                } else {
                    throw new SqlExecutionException(plan, executionState, cleanupFailure);
                }
            }
        }
    }

    private void validate(ExecutionPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (plan.getSql().isBlank()) {
            throw new IllegalArgumentException("sql must not be blank");
        }
        if (plan.getStatementType() == ExecutionPlan.StatementType.BATCH
                && !(plan instanceof BatchExecutionPlan)) {
            throw new IllegalArgumentException("BATCH execution requires BatchExecutionPlan");
        }
        if (plan.returnsGeneratedKey()
                && plan.getStatementType() != ExecutionPlan.StatementType.INSERT) {
            throw new IllegalArgumentException("Generated keys require an INSERT plan");
        }
    }

    private void invokeBefore(ExecutionPlan plan, List<ExecutionInterceptor> entered) {
        for (ExecutionInterceptor interceptor : interceptors) {
            entered.add(interceptor);
            interceptor.beforeExecution(plan);
        }
    }

    private void invokeSuccess(List<ExecutionInterceptor> entered, ExecutionOutcome outcome) {
        for (int index = entered.size() - 1; index >= 0; index--) {
            entered.get(index).afterSuccess(outcome);
        }
    }

    private void invokeFailure(
            List<ExecutionInterceptor> entered, ExecutionOutcome outcome, Throwable primaryFailure) {
        for (int index = entered.size() - 1; index >= 0; index--) {
            try {
                entered.get(index).afterFailure(outcome);
            } catch (Throwable callbackFailure) {
                primaryFailure.addSuppressed(callbackFailure);
            }
        }
    }

    private PreparedStatement prepare(Connection connection, ExecutionPlan plan) throws SQLException {
        return plan.returnsGeneratedKey()
            ? connection.prepareStatement(plan.getSql(), Statement.RETURN_GENERATED_KEYS)
            : connection.prepareStatement(plan.getSql());
    }

    private Object readGeneratedKey(ResultSet generatedKeys) throws SQLException {
        if (!generatedKeys.next()) {
            throw new SQLException("JDBC returned no generated key");
        }
        Object generatedKey = generatedKeys.getObject(1);
        if (generatedKeys.next()) {
            throw new SQLException("JDBC returned multiple generated keys for one insert");
        }
        return generatedKey;
    }

    private void addBatch(PreparedStatement statement, BatchExecutionPlan plan) throws SQLException {
        List<Object[]> batchParameters = plan.getBatchParameters();
        for (Object[] parameters : batchParameters) {
            bind(statement, parameters, plan.getParameterBinders());
            statement.addBatch();
        }
    }

    private void bind(
            PreparedStatement statement, Object[] parameters, ParameterBinder<?>[] binders) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            ParameterBinder<Object> binder = binderAt(binders, index);
            if (binder == null) {
                statement.setObject(index + 1, parameters[index]);
            } else {
                binder.bind(statement, index + 1, parameters[index]);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private ParameterBinder<Object> binderAt(ParameterBinder<?>[] binders, int index) {
        return binders == null || index >= binders.length
            ? null
            : (ParameterBinder<Object>) binders[index];
    }

    private List<Object[]> readRows(ResultSet resultSet, RowMapper<?> rowMapper) throws SQLException {
        if (rowMapper != null) {
            List<Object[]> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(new Object[]{rowMapper.map(resultSet)});
            }
            return rows;
        }

        ResultSetMetaData metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();
        List<Object[]> rows = new ArrayList<>();
        while (resultSet.next()) {
            Object[] row = new Object[columnCount];
            for (int column = 1; column <= columnCount; column++) {
                row[column - 1] = resultSet.getObject(column);
            }
            rows.add(row);
        }
        return rows;
    }

    private Throwable closeResources(
            ResultSet resultSet, PreparedStatement statement, ConnectionHandle connectionHandle) {
        Throwable failure = close(resultSet, null);
        failure = close(statement, failure);
        return close(connectionHandle, failure);
    }

    private Throwable close(AutoCloseable resource, Throwable primaryFailure) {
        if (resource == null) {
            return primaryFailure;
        }
        try {
            resource.close();
        } catch (Throwable closeFailure) {
            if (primaryFailure == null) {
                return closeFailure;
            }
            primaryFailure.addSuppressed(closeFailure);
        }
        return primaryFailure;
    }

    private void appendFlattened(Throwable primaryFailure, Throwable cleanupFailure) {
        primaryFailure.addSuppressed(cleanupFailure);
        for (Throwable suppressed : cleanupFailure.getSuppressed()) {
            primaryFailure.addSuppressed(suppressed);
        }
    }

    private long elapsed(long startedAt) {
        return Math.max(0L, System.nanoTime() - startedAt);
    }

    private int affectedRows(SqlResult result) {
        return result == null || result.isQuery() ? 0 : result.getUpdateCount();
    }

    private int resultCount(SqlResult result) {
        return result == null || result.getQueryResults() == null ? 0 : result.getQueryResults().size();
    }
}

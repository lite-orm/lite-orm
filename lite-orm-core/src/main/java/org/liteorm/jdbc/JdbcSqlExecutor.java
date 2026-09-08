package org.liteorm.jdbc;

import org.liteorm.api.BatchExecutionPlan;
import org.liteorm.api.ConnectionHandle;
import org.liteorm.api.ConnectionHandleFactory;
import org.liteorm.api.CursorCallback;
import org.liteorm.api.ExecutionInterceptor;
import org.liteorm.api.ExecutionOutcome;
import org.liteorm.api.ExecutionPhase;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.JdbcExecutionState;
import org.liteorm.api.ParameterBinder;
import org.liteorm.api.RowMapper;
import org.liteorm.api.ResultColumn;
import org.liteorm.api.RowCursor;
import org.liteorm.api.SqlExecutionException;
import org.liteorm.api.SqlExecutor;
import org.liteorm.api.SqlResult;
import org.liteorm.api.StatementOptions;

import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Executes immutable plans through one fixed, non-configurable JDBC lifecycle.
 */
public final class JdbcSqlExecutor implements SqlExecutor {

    private static final System.Logger LOGGER = System.getLogger(JdbcSqlExecutor.class.getName());

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
        int confirmedAffectedRows = 0;
        JdbcExecutionState executionState = JdbcExecutionState.NOT_EXECUTED;
        ExecutionPhase phase = ExecutionPhase.PREPARATION;
        Throwable executionFailure = null;

        try {
            invokeBefore(plan, entered);
            connectionHandle = Objects.requireNonNull(
                connectionHandleFactory.openHandle(), "connectionHandleFactory returned null");
            Connection connection = Objects.requireNonNull(
                connectionHandle.connection(), "connectionHandle returned null connection");
            statement = prepare(connection, plan);
            applyOptions(statement, plan.getStatementOptions());
            switch (plan.getStatementType()) {
                case SELECT -> {
                    phase = ExecutionPhase.BINDING;
                    bind(statement, plan.getParameters(), plan.getParameterBinders(), plan.getTypeRouting());
                    phase = ExecutionPhase.EXECUTION;
                    executionState = JdbcExecutionState.OUTCOME_UNKNOWN;
                    resultSet = statement.executeQuery();
                    executionState = JdbcExecutionState.EXECUTED;
                    phase = plan.getRowMapper() == null && plan.getTypeRouting() == null
                        ? ExecutionPhase.RESULT_READING : ExecutionPhase.MAPPING;
                    QueryRows queryRows = readRows(resultSet, plan.getRowMapper(), plan.getTypeRouting());
                    result = SqlResult.forQuery(queryRows.columns(), queryRows.rows());
                }
                case INSERT, UPDATE, DELETE -> {
                    phase = ExecutionPhase.BINDING;
                    bind(statement, plan.getParameters(), plan.getParameterBinders(), plan.getTypeRouting());
                    phase = ExecutionPhase.EXECUTION;
                    executionState = JdbcExecutionState.OUTCOME_UNKNOWN;
                    int updateCount = statement.executeUpdate();
                    executionState = JdbcExecutionState.EXECUTED;
                    confirmedAffectedRows = updateCount;
                    if (plan.returnsGeneratedKey()) {
                        phase = plan.getRowMapper() == null && plan.getTypeRouting() == null
                            ? ExecutionPhase.RESULT_READING : ExecutionPhase.MAPPING;
                        resultSet = statement.getGeneratedKeys();
                        result = SqlResult.forGeneratedKey(
                            updateCount, readGeneratedKey(
                                resultSet, plan.getRowMapper(), plan.getTypeRouting()));
                    } else {
                        result = SqlResult.forUpdate(updateCount);
                    }
                }
                case BATCH -> {
                    BatchExecutionPlan batchPlan = (BatchExecutionPlan) plan;
                    phase = ExecutionPhase.BINDING;
                    addBatch(statement, batchPlan);
                    phase = ExecutionPhase.EXECUTION;
                    int[] updateCounts;
                    if (batchPlan.getBatchParameters().isEmpty()) {
                        updateCounts = new int[0];
                    } else {
                        executionState = JdbcExecutionState.OUTCOME_UNKNOWN;
                        updateCounts = statement.executeBatch();
                        executionState = JdbcExecutionState.EXECUTED;
                    }
                    result = SqlResult.forBatch(updateCounts);
                }
            }
        } catch (Throwable failure) {
            executionFailure = failure;
        }

        ExecutionOutcome outcome = completeExecution(
            plan, phase, executionState, executionFailure, true,
            resultSet, statement, connectionHandle, startedAt,
            result == null ? confirmedAffectedRows : affectedRows(result), resultCount(result));
        invokeTerminal(entered, outcome);
        throwIfFailed(outcome);
        return result;
    }

    @Override
    public <T, R> R queryCursor(ExecutionPlan plan, CursorCallback<T, R> callback) {
        validateCursor(plan, callback);
        long startedAt = System.nanoTime();
        List<ExecutionInterceptor> entered = new ArrayList<>(interceptors.size());
        ConnectionHandle connectionHandle = null;
        PreparedStatement statement = null;
        ResultSet resultSet = null;
        JdbcRowCursor<T> cursor = null;
        R callbackResult = null;
        JdbcExecutionState executionState = JdbcExecutionState.NOT_EXECUTED;
        ExecutionPhase phase = ExecutionPhase.PREPARATION;
        Throwable executionFailure = null;
        boolean wrapExecutionFailure = true;

        try {
            invokeBefore(plan, entered);
            connectionHandle = Objects.requireNonNull(
                connectionHandleFactory.openHandle(), "connectionHandleFactory returned null");
            Connection connection = Objects.requireNonNull(
                connectionHandle.connection(), "connectionHandle returned null connection");
            statement = prepare(connection, plan);
            applyOptions(statement, plan.getStatementOptions());
            phase = ExecutionPhase.BINDING;
            bind(statement, plan.getParameters(), plan.getParameterBinders(), plan.getTypeRouting());
            phase = ExecutionPhase.EXECUTION;
            executionState = JdbcExecutionState.OUTCOME_UNKNOWN;
            resultSet = statement.executeQuery();
            executionState = JdbcExecutionState.EXECUTED;
            phase = ExecutionPhase.MAPPING;
            cursor = new JdbcRowCursor<>(resultSet, rowMapper(plan));
            callbackResult = callback.consume(cursor);
        } catch (Throwable failure) {
            if (failure instanceof CursorReadException cursorFailure) {
                executionFailure = cursorFailure.getCause();
            } else {
                executionFailure = failure;
                wrapExecutionFailure = !(failure instanceof RuntimeException);
            }
        }

        int rowsRead = cursor == null ? 0 : cursor.rowsRead();
        if (cursor != null) {
            cursor.deactivate();
        }
        ExecutionOutcome outcome = completeExecution(
            plan, phase, executionState, executionFailure, wrapExecutionFailure,
            resultSet, statement, connectionHandle, startedAt, 0, rowsRead);
        invokeTerminal(entered, outcome);
        throwIfFailed(outcome);
        return callbackResult;
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

    private <T, R> void validateCursor(ExecutionPlan plan, CursorCallback<T, R> callback) {
        validate(plan);
        Objects.requireNonNull(callback, "callback");
        if (plan.getStatementType() != ExecutionPlan.StatementType.SELECT) {
            throw new IllegalArgumentException("Cursor queries require a SELECT plan");
        }
        if (plan.getRowMapper() == null) {
            throw new IllegalArgumentException("Cursor queries require a RowMapper");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> RowMapper<T> rowMapper(ExecutionPlan plan) {
        return (RowMapper<T>) plan.getRowMapper();
    }

    private void invokeBefore(ExecutionPlan plan, List<ExecutionInterceptor> entered) {
        for (ExecutionInterceptor interceptor : interceptors) {
            interceptor.beforeExecution(plan);
            entered.add(interceptor);
        }
    }

    private void invokeSuccess(List<ExecutionInterceptor> entered, ExecutionOutcome outcome) {
        for (int index = entered.size() - 1; index >= 0; index--) {
            ExecutionInterceptor interceptor = entered.get(index);
            try {
                interceptor.afterSuccess(outcome);
            } catch (RuntimeException callbackFailure) {
                logTerminalFailure(interceptor, outcome, callbackFailure);
            }
        }
    }

    private void invokeFailure(List<ExecutionInterceptor> entered, ExecutionOutcome outcome) {
        for (int index = entered.size() - 1; index >= 0; index--) {
            ExecutionInterceptor interceptor = entered.get(index);
            try {
                interceptor.afterFailure(outcome);
            } catch (RuntimeException callbackFailure) {
                logTerminalFailure(interceptor, outcome, callbackFailure);
            }
        }
    }

    private void logTerminalFailure(
            ExecutionInterceptor interceptor, ExecutionOutcome outcome, RuntimeException failure) {
        LOGGER.log(
            System.Logger.Level.WARNING,
            "LiteORM interceptor terminal callback failed [interceptor="
                + interceptor.getClass().getName()
                + ", statementId=" + outcome.plan().getStatementId()
                + ", executionState=" + outcome.executionState() + ']'
            , failure
        );
    }

    private PreparedStatement prepare(Connection connection, ExecutionPlan plan) throws SQLException {
        return plan.returnsGeneratedKey()
            ? connection.prepareStatement(plan.getSql(), new String[]{plan.getGeneratedKeyColumn()})
            : connection.prepareStatement(plan.getSql());
    }

    private void applyOptions(PreparedStatement statement, StatementOptions options) throws SQLException {
        if (options.timeoutSeconds() != null) {
            statement.setQueryTimeout(options.timeoutSeconds());
        }
        if (options.fetchSize() != null) {
            statement.setFetchSize(options.fetchSize());
        }
        if (options.maxRows() != null) {
            statement.setMaxRows(options.maxRows());
        }
    }

    private Object readGeneratedKey(
            ResultSet generatedKeys,
            RowMapper<?> rowMapper,
            ExecutionPlan.TypeRouting typeRouting) throws SQLException {
        ResultSetMetaData metadata = generatedKeys.getMetaData();
        int columnCount = metadata.getColumnCount();
        if (columnCount != 1) {
            throw new SQLException("JDBC returned a composite generated key with " + columnCount + " columns");
        }
        if (!generatedKeys.next()) {
            throw new SQLException("JDBC returned no generated key");
        }
        Object generatedKey;
        if (rowMapper != null) {
            generatedKey = rowMapper.map(generatedKeys);
        } else if (typeRouting != null && typeRouting.resultTypes().length == 1) {
            TypeHandlerManager.ResultHandler handler = typeRouting.manager().resolveResult(
                metadata, 1, typeRouting.resultTypes()[0]);
            generatedKey = handler.getResult(generatedKeys, 1);
        } else {
            generatedKey = generatedKeys.getObject(1);
        }
        if (generatedKeys.next()) {
            throw new SQLException("JDBC returned multiple generated keys for one insert");
        }
        return generatedKey;
    }

    private void addBatch(PreparedStatement statement, BatchExecutionPlan plan) throws SQLException {
        List<Object[]> batchParameters = plan.getBatchParameters();
        for (Object[] parameters : batchParameters) {
            bind(statement, parameters, plan.getParameterBinders(), plan.getTypeRouting());
            statement.addBatch();
        }
    }

    private void bind(
            PreparedStatement statement,
            Object[] parameters,
            ParameterBinder<?>[] binders,
            ExecutionPlan.TypeRouting typeRouting) throws SQLException {
        Class<?>[] parameterTypes = typeRouting == null ? null : typeRouting.parameterTypes();
        JDBCType[] parameterJdbcTypes = typeRouting == null ? null : typeRouting.parameterJdbcTypes();
        for (int index = 0; index < parameters.length; index++) {
            ParameterBinder<Object> binder = binderAt(binders, index);
            if (binder != null) {
                binder.bind(statement, index + 1, parameters[index]);
            } else if (typeRouting != null && index < parameterTypes.length) {
                JDBCType jdbcType = parameterJdbcTypes == null ? null : parameterJdbcTypes[index];
                typeRouting.manager().setParameter(
                    statement, index + 1, parameters[index], parameterTypes[index], jdbcType);
            } else {
                bindDefault(statement, index + 1, parameters[index]);
            }
        }
    }

    private void bindDefault(PreparedStatement statement, int index, Object value) throws SQLException {
        statement.setObject(index, value);
    }

    @SuppressWarnings("unchecked")
    private ParameterBinder<Object> binderAt(ParameterBinder<?>[] binders, int index) {
        return binders == null || index >= binders.length
            ? null
            : (ParameterBinder<Object>) binders[index];
    }

    private QueryRows readRows(
            ResultSet resultSet,
            RowMapper<?> rowMapper,
            ExecutionPlan.TypeRouting typeRouting) throws SQLException {
        if (rowMapper != null) {
            List<Object[]> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(new Object[]{rowMapper.map(resultSet)});
            }
            return new QueryRows(List.of(), rows);
        }

        ResultSetMetaData metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();
        List<ResultColumn> columns = new ArrayList<>(columnCount);
        for (int column = 1; column <= columnCount; column++) {
            String label = metadata.getColumnLabel(column);
            if (label == null || label.isBlank()) {
                label = metadata.getColumnName(column);
            }
            if (label == null || label.isBlank()) {
                label = "column" + column;
            }
            columns.add(new ResultColumn(label, column - 1));
        }
        TypeHandlerManager.ResultHandler[] handlers = resolveResultHandlers(metadata, columns, typeRouting);
        List<Object[]> rows = new ArrayList<>();
        while (resultSet.next()) {
            Object[] row = new Object[columnCount];
            for (int column = 1; column <= columnCount; column++) {
                TypeHandlerManager.ResultHandler handler = handlers[column - 1];
                row[column - 1] = handler == null
                    ? readColumnValue(resultSet, metadata, column)
                    : handler.getResult(resultSet, column);
            }
            rows.add(row);
        }
        return new QueryRows(columns, rows);
    }

    private TypeHandlerManager.ResultHandler[] resolveResultHandlers(
            ResultSetMetaData metadata,
            List<ResultColumn> columns,
            ExecutionPlan.TypeRouting typeRouting) throws SQLException {
        TypeHandlerManager.ResultHandler[] handlers =
            new TypeHandlerManager.ResultHandler[columns.size()];
        if (typeRouting == null) {
            return handlers;
        }
        Class<?>[] resultTypes = typeRouting.resultTypes();
        String[] labels = typeRouting.resultColumnLabels();
        if (labels == null && resultTypes.length == 1 && !columns.isEmpty()) {
            handlers[0] = typeRouting.manager().resolveResult(metadata, 1, resultTypes[0]);
            return handlers;
        }
        if (labels == null) {
            return handlers;
        }
        for (int target = 0; target < labels.length; target++) {
            int column = findColumn(columns, labels[target]);
            if (column >= 0) {
                handlers[column] = typeRouting.manager().resolveResult(
                    metadata, column + 1, resultTypes[target]);
            }
        }
        return handlers;
    }

    private int findColumn(List<ResultColumn> columns, String requiredLabel) {
        String normalized = requiredLabel.trim().toLowerCase(Locale.ROOT);
        for (ResultColumn column : columns) {
            if (column.label().trim().toLowerCase(Locale.ROOT).equals(normalized)) {
                return column.index();
            }
        }
        return -1;
    }

    private Object readColumnValue(ResultSet resultSet, ResultSetMetaData metadata, int column)
            throws SQLException {
        if (metadata.getColumnType(column) == Types.TIME) {
            return resultSet.getObject(column, LocalTime.class);
        }
        return resultSet.getObject(column);
    }

    private record QueryRows(List<ResultColumn> columns, List<Object[]> rows) {
    }

    private static final class JdbcRowCursor<T> implements RowCursor<T> {

        private final ResultSet resultSet;
        private final RowMapper<T> rowMapper;
        private boolean active = true;
        private boolean positioned;
        private T current;
        private int rowsRead;

        private JdbcRowCursor(ResultSet resultSet, RowMapper<T> rowMapper) {
            this.resultSet = resultSet;
            this.rowMapper = rowMapper;
        }

        @Override
        public boolean next() {
            requireActive();
            try {
                if (!resultSet.next()) {
                    positioned = false;
                    current = null;
                    return false;
                }
                current = rowMapper.map(resultSet);
                positioned = true;
                rowsRead++;
                return true;
            } catch (SQLException exception) {
                throw new CursorReadException(exception);
            }
        }

        @Override
        public T current() {
            requireActive();
            if (!positioned) {
                throw new IllegalStateException("Cursor is not positioned on a row");
            }
            return current;
        }

        private int rowsRead() {
            return rowsRead;
        }

        private void deactivate() {
            active = false;
            positioned = false;
            current = null;
        }

        private void requireActive() {
            if (!active) {
                throw new IllegalStateException("Cursor is no longer active");
            }
        }
    }

    private static final class CursorReadException extends RuntimeException {

        private CursorReadException(SQLException cause) {
            super(cause);
        }
    }

    private ExecutionOutcome completeExecution(
            ExecutionPlan plan,
            ExecutionPhase phase,
            JdbcExecutionState executionState,
            Throwable executionFailure,
            boolean wrapExecutionFailure,
            ResultSet resultSet,
            PreparedStatement statement,
            ConnectionHandle connectionHandle,
            long startedAt,
            int affectedRows,
            int resultCount) {
        Throwable finalFailure = finalFailure(
            plan, phase, executionState, executionFailure,
            collectCleanupFailures(resultSet, statement, connectionHandle), wrapExecutionFailure);
        long durationNanos = elapsed(startedAt);
        return finalFailure == null
            ? ExecutionOutcome.success(
                plan, executionState, durationNanos, affectedRows, resultCount)
            : ExecutionOutcome.failure(
                plan, executionState, durationNanos, affectedRows, resultCount, finalFailure);
    }

    private void invokeTerminal(List<ExecutionInterceptor> entered, ExecutionOutcome outcome) {
        if (outcome.failed()) {
            invokeFailure(entered, outcome);
        } else {
            invokeSuccess(entered, outcome);
        }
    }

    private void throwIfFailed(ExecutionOutcome outcome) {
        Throwable finalFailure = outcome.failure();
        if (finalFailure instanceof Error error) {
            throw error;
        }
        if (finalFailure != null) {
            throw (RuntimeException) finalFailure;
        }
    }

    private List<Throwable> collectCleanupFailures(
            ResultSet resultSet, PreparedStatement statement, ConnectionHandle connectionHandle) {
        List<Throwable> failures = new ArrayList<>(3);
        collectCloseFailure(resultSet, failures);
        collectCloseFailure(statement, failures);
        collectCloseFailure(connectionHandle, failures);
        return failures;
    }

    private void collectCloseFailure(AutoCloseable resource, List<Throwable> failures) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Throwable closeFailure) {
            failures.add(closeFailure);
        }
    }

    private Throwable finalFailure(
            ExecutionPlan plan,
            ExecutionPhase phase,
            JdbcExecutionState executionState,
            Throwable executionFailure,
            List<Throwable> cleanupFailures,
            boolean wrapExecutionFailure) {
        if (executionFailure != null) {
            appendSuppressedOnce(executionFailure, cleanupFailures);
            if (executionFailure instanceof Error || !wrapExecutionFailure) {
                return executionFailure;
            }
            return new SqlExecutionException(plan, phase, executionState, executionFailure);
        }
        if (cleanupFailures.isEmpty()) {
            return null;
        }
        Throwable cleanupFailure = cleanupFailures.get(0);
        appendSuppressedOnce(cleanupFailure, cleanupFailures.subList(1, cleanupFailures.size()));
        return new SqlExecutionException(
            plan, ExecutionPhase.CLEANUP, executionState, cleanupFailure);
    }

    private void appendSuppressedOnce(Throwable primaryFailure, List<Throwable> additionalFailures) {
        for (Throwable additionalFailure : additionalFailures) {
            if (additionalFailure == primaryFailure || isSuppressed(primaryFailure, additionalFailure)) {
                continue;
            }
            primaryFailure.addSuppressed(additionalFailure);
        }
    }

    private boolean isSuppressed(Throwable primaryFailure, Throwable candidate) {
        for (Throwable suppressed : primaryFailure.getSuppressed()) {
            if (suppressed == candidate) {
                return true;
            }
        }
        return false;
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

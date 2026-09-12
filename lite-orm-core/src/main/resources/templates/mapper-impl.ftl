package ${packageName};

import org.liteorm.api.BatchExecutionPlan;
import org.liteorm.api.BatchDefinition;
import org.liteorm.api.BoundSql;
import org.liteorm.api.BoundSqlBuilder;
import org.liteorm.api.BatchResult;
import org.liteorm.api.CommandDefinition;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.GeneratedKeyResult;
import org.liteorm.api.ParameterBinder;
import org.liteorm.api.QueryDefinition;
import org.liteorm.api.QueryExecutionPlan;
import org.liteorm.api.QueryResult;
import org.liteorm.api.SqlExecutor;
import org.liteorm.api.SqlResult;
import org.liteorm.api.StatementOptions;
import org.liteorm.api.UpdateResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Generated Mapper implementation.
 * Contains compile-time SQL binding and result mapping without reflection.
 */
public class ${implClassName} implements ${interfaceName} {

    private final SqlExecutor sqlExecutor;

    public ${implClassName}(SqlExecutor sqlExecutor) {
        this.sqlExecutor = java.util.Objects.requireNonNull(sqlExecutor, "sqlExecutor");
    }

${generatedMethods}

}

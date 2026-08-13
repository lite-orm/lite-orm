package org.liteorm.api;

import java.util.Map;

/**
 * 生成代码提交给运行时的标准执行计划。
 *
 * @author lite-orm
 * @since 2026/03/26
 */
public interface ExecutionPlan {

    String getStatementId();

    String getSql();

    Object[] getParameters();

    Map<String, Object> getParameterMap();

    boolean usesParameterMap();

    StatementType getStatementType();

    boolean requiresTransaction();

    String getResultType();

    SqlSource getSourceType();

    default ParameterBinder<?>[] getParameterBinders() {
        return null;
    }

    default RowMapper<?> getRowMapper() {
        return null;
    }

    enum StatementType {
        SELECT,
        INSERT,
        UPDATE,
        DELETE,
        BATCH
    }

    enum SqlSource {
        XML,
        ANNOTATION,
        SCRIPT,
        GENERATED
    }
}

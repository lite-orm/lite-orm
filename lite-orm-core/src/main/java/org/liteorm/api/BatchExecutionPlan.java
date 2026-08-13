package org.liteorm.api;

import java.util.List;

/**
 * Execution plan containing ordered parameter sets for one JDBC batch.
 */
public interface BatchExecutionPlan extends ExecutionPlan {

    List<Object[]> getBatchParameters();
}

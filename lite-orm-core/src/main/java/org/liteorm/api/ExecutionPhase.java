package org.liteorm.api;

public enum ExecutionPhase {
    CONFIGURATION,
    TRANSACTION,
    PREPARATION,
    BINDING,
    EXECUTION,
    RESULT_READING,
    MAPPING,
    CLEANUP
}

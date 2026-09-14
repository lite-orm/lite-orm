package io.github.lynxus.api;

public class MappingException extends LynxusException {

    private final String statementId;
    private final Class<?> targetType;
    private final String columnLabel;
    private final Integer columnIndex;

    public MappingException(
            String message,
            String statementId,
            Class<?> targetType,
            String columnLabel,
            Integer columnIndex,
            Throwable cause) {
        super(buildMessage(message, statementId, targetType, columnLabel, columnIndex), cause);
        this.statementId = statementId;
        this.targetType = targetType;
        this.columnLabel = columnLabel;
        this.columnIndex = columnIndex;
    }

    public ExecutionPhase getPhase() {
        return ExecutionPhase.MAPPING;
    }

    public String getStatementId() {
        return statementId;
    }

    public Class<?> getTargetType() {
        return targetType;
    }

    public String getColumnLabel() {
        return columnLabel;
    }

    public Integer getColumnIndex() {
        return columnIndex;
    }

    private static String buildMessage(
            String message,
            String statementId,
            Class<?> targetType,
            String columnLabel,
            Integer columnIndex) {
        StringBuilder result = new StringBuilder(message)
            .append(" [statementId=").append(statementId)
            .append(", phase=").append(ExecutionPhase.MAPPING)
            .append(", targetType=").append(targetType == null ? "null" : targetType.getName());
        if (columnLabel != null) {
            result.append(", columnLabel=").append(columnLabel);
        }
        if (columnIndex != null) {
            result.append(", columnIndex=").append(columnIndex);
        }
        return result.append(']').toString();
    }
}

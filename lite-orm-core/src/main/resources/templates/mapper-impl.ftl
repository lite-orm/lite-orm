package ${packageName};

import org.liteorm.api.BatchExecutionPlan;
import org.liteorm.api.BatchDefinition;
import org.liteorm.api.BoundSql;
import org.liteorm.api.CommandDefinition;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.MappingException;
import org.liteorm.api.NonUniqueResultException;
import org.liteorm.api.ParameterBinder;
import org.liteorm.api.QueryDefinition;
import org.liteorm.api.QueryExecutionPlan;
import org.liteorm.api.SqlExecutor;
import org.liteorm.api.SqlResult;
import org.liteorm.api.StatementOptions;
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

    private void appendSqlFragment(StringBuilder sql, String fragment) {
        if (fragment == null || fragment.isBlank()) {
            return;
        }
        String normalized = fragment.trim();
        if (!sql.isEmpty() && needsSqlSeparator(sql.charAt(sql.length() - 1), normalized.charAt(0))) {
            sql.append(' ');
        }
        sql.append(normalized);
    }

    private boolean needsSqlSeparator(char previous, char next) {
        return !Character.isWhitespace(previous)
            && !Character.isWhitespace(next)
            && previous != '('
            && next != ')'
            && next != ','
            && previous != ',';
    }

    private String normalizeWhereClause(String rawClause) {
        String normalized = rawClause == null ? "" : rawClause.trim();
        normalized = normalized.replaceFirst("^(?i)(AND|OR)\\s+", "");
        return normalized.trim();
    }

    private String normalizeSetClause(String rawClause) {
        String normalized = rawClause == null ? "" : rawClause.trim();
        while (normalized.endsWith(",")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private String applyTrim(String rawClause, String prefix, String suffix, String prefixOverrides, String suffixOverrides) {
        String normalized = rawClause == null ? "" : rawClause.trim();
        normalized = stripOverrides(normalized, prefixOverrides, true);
        normalized = stripOverrides(normalized, suffixOverrides, false);
        if (normalized.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (prefix != null && !prefix.isBlank()) {
            builder.append(prefix.trim()).append(" ");
        }
        builder.append(normalized);
        if (suffix != null && !suffix.isBlank()) {
            builder.append(" ").append(suffix.trim());
        }
        return builder.toString();
    }

    private String stripOverrides(String clause, String overrides, boolean prefix) {
        if (overrides == null || overrides.isBlank()) {
            return clause == null ? "" : clause.trim();
        }
        String normalized = clause == null ? "" : clause.trim();
        for (String token : overrides.split("\\|")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (prefix && normalized.regionMatches(true, 0, trimmed, 0, trimmed.length())) {
                normalized = normalized.substring(trimmed.length()).trim();
            }
            if (!prefix && normalized.length() >= trimmed.length()
                && normalized.regionMatches(true, normalized.length() - trimmed.length(), trimmed, 0, trimmed.length())) {
                normalized = normalized.substring(0, normalized.length() - trimmed.length()).trim();
            }
        }
        return normalized;
    }
}

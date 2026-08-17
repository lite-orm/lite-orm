package org.liteorm.compile;

import javax.lang.model.element.ExecutableElement;
import java.util.List;

/**
 * Parses annotation or XML SQL into a normalized dynamic SQL model.
 * 
 * @author lite-orm
 * @since 2024/10/01
 */
interface SqlContentParser {
    
    /**
     * Parses SQL for one Mapper method.
     * 
     * @param method Mapper method
     * @return parsed SQL, or {@code null} when this parser does not provide SQL
     */
    SqlParseResult parseSql(ExecutableElement method);
    
    /**
     * Returns whether this parser can inspect the method.
     * 
     * @param method Mapper method
     * @return whether the parser supports the method
     */
    boolean supports(ExecutableElement method);
    
    /**
     * Returns the parser name used in diagnostics.
     * 
     * @return parser name
     */
    String getParserName();
    
    /**
     * Parsed SQL metadata.
     */
    record SqlParseResult(
        String sqlTemplate,
        SqlType sqlType,
        SqlSourceType sourceType,
        boolean isDynamic,
        List<ParameterInfo> parameters,
        AstNode astNode
    ) {}
    
    /**
     * Compile-time parameter metadata.
     */
    record ParameterInfo(
        String name,
        String accessCode,
        String typeName
    ) {}
    
    /**
     * SQL source type.
     */
    enum SqlSourceType {
        XML,
        ANNOTATION,
        SCRIPT
    }
    
    /**
     * SQL statement type.
     */
    enum SqlType {
        SELECT, INSERT, UPDATE, DELETE, BATCH
    }
}

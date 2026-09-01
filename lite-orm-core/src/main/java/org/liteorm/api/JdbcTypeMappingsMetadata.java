package org.liteorm.api;

/**
 * Exposes the JDBC type mappings collection selected for a generated Mapper.
 *
 * <p>This metadata identifies the compile-time collection only. It does not provide
 * runtime mapping lookup or replacement.</p>
 */
public interface JdbcTypeMappingsMetadata {

    /**
     * Returns the selected JDBC type mappings collection.
     */
    Class<? extends JdbcTypeMappings> jdbcTypeMappings();
}

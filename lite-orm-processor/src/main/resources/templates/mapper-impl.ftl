package ${mapper.packageName};

<#list mapper.imports as importName>
import ${importName};
</#list>

/**
 * Generated Mapper implementation.
 * Contains compile-time SQL binding and result mapping without reflection.
 */
public class ${mapper.implementationName} implements ${mapper.interfaceName} {

    private final SqlExecutor sqlExecutor;

    public ${mapper.implementationName}(SqlExecutor sqlExecutor) {
        this.sqlExecutor = java.util.Objects.requireNonNull(sqlExecutor, "sqlExecutor");
    }

<#if mapper.fields?size gt 0>
<#list mapper.fields as field>
${field.declaration}
</#list>

</#if>
<#list mapper.members as member>
${member.source}

</#list>

}

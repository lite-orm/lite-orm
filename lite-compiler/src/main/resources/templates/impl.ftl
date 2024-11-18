package ${packageName};

public class ${className} implements ${interfaceName} {

<#list methods as method>
    @Override
    ${signatureFullName} {
        List<Object> param = new ArrayList();
        StringBuilder builder = new StringBuilder();
        ${parsedContent}
        // SQL执行逻辑
        return null;
    }
</#list>

}
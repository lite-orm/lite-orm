package org.liteorm.compile;

import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL参数解析器 - 基于物理必需性的#{param}语法处理
 * 
 * 物理原理：
 * 1. SQL中的#{param}对应PreparedStatement的?占位符（物理必需：防SQL注入）
 * 2. 参数名称对应方法参数名（物理必需：参数绑定）
 * 3. 参数顺序对应PreparedStatement.setXxx的索引（物理必需：JDBC协议）
 * 4. 参数类型对应具体的setXxx方法（物理必需：类型安全）
 * 
 * 设计原则：
 * - 编译期解析和验证所有参数
 * - 生成硬编码的参数绑定逻辑
 * - 支持复杂参数（对象属性、嵌套访问）
 * - 提供详细的编译期错误信息
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class SqlParameterParser {
    
    // #{param} 语法的正则表达式
    private static final Pattern PARAM_PATTERN = Pattern.compile("#\\{([^}]+)\\}");
    
    /**
     * 解析SQL中的参数信息
     * 
     * @param sql 原始SQL语句
     * @param methodParameters 方法参数列表
     * @return 解析后的SQL参数信息
     */
    public SqlParseResult parseSql(String sql, List<? extends VariableElement> methodParameters) {
        List<ParameterInfo> parameters = new ArrayList<>();
        Map<String, VariableElement> parameterMap = buildParameterMap(methodParameters);
        
        // 解析SQL中的所有#{param}
        Matcher matcher = PARAM_PATTERN.matcher(sql);
        String processedSql = sql;
        int parameterIndex = 1; // PreparedStatement参数索引从1开始
        
        while (matcher.find()) {
            String paramExpression = matcher.group(1).trim();
            ParameterInfo paramInfo = parseParameterExpression(paramExpression, parameterMap, parameterIndex);
            parameters.add(paramInfo);
            parameterIndex++;
        }
        
        // 保持#{param}格式，不转换为?占位符
        // LiteORM使用#{param}语法，在运行时动态替换
        
        return new SqlParseResult(processedSql, parameters);
    }
    
    /**
     * 构建参数名到参数元素的映射
     */
    private Map<String, VariableElement> buildParameterMap(List<? extends VariableElement> methodParameters) {
        Map<String, VariableElement> parameterMap = new HashMap<>();
        for (VariableElement param : methodParameters) {
            parameterMap.put(param.getSimpleName().toString(), param);
        }
        return parameterMap;
    }
    
    /**
     * 解析参数表达式
     * 支持：
     * - 简单参数：#{id}
     * - 对象属性：#{user.name}
     * - 数组/集合：#{ids[0]}（暂不支持，留待后续实现）
     */
    private ParameterInfo parseParameterExpression(String expression, 
                                                 Map<String, VariableElement> parameterMap, 
                                                 int index) {
        // 简单参数处理
        if (!expression.contains(".")) {
            VariableElement param = parameterMap.get(expression);
            if (param == null) {
                throw new IllegalArgumentException("Parameter not found: " + expression);
            }
            
            return new ParameterInfo(
                index,
                expression,
                param.getSimpleName().toString(),
                param.asType().toString(),
                ParameterInfo.AccessType.DIRECT,
                null
            );
        }
        
        // 对象属性处理：user.name
        String[] parts = expression.split("\\.", 2);
        String objectName = parts[0];
        String propertyPath = parts[1];
        
        VariableElement param = parameterMap.get(objectName);
        if (param == null) {
            throw new IllegalArgumentException("Parameter object not found: " + objectName);
        }
        
        return new ParameterInfo(
            index,
            expression,
            objectName,
            param.asType().toString(),
            ParameterInfo.AccessType.PROPERTY,
            propertyPath
        );
    }
    
    /**
     * 生成参数绑定代码
     */
    public String generateParameterBindingCode(List<ParameterInfo> parameters) {
        if (parameters.isEmpty()) {
            return "        Object[] params = new Object[0];\n";
        }
        
        StringBuilder code = new StringBuilder();
        code.append("        // 类型安全的参数绑定 - 编译期硬编码\n");
        code.append("        Object[] params = new Object[").append(parameters.size()).append("];\n");
        
        for (ParameterInfo param : parameters) {
            code.append("        params[").append(param.getIndex() - 1).append("] = ");
            
            switch (param.getAccessType()) {
                case DIRECT:
                    // 直接参数：params[0] = id;
                    code.append(param.getParameterName()).append(";\n");
                    break;
                    
                case PROPERTY:
                    // 对象属性：params[0] = user.getName();
                    code.append(generatePropertyAccess(param)).append(";\n");
                    break;
                    
                default:
                    throw new UnsupportedOperationException("Unsupported access type: " + param.getAccessType());
            }
        }
        
        return code.toString();
    }
    
    /**
     * 生成属性访问代码
     */
    private String generatePropertyAccess(ParameterInfo param) {
        String objectName = param.getParameterName();
        String propertyPath = param.getPropertyPath();
        
        // 简单属性访问：user.name -> user.getName() 或 user.name()（record class）
        if (!propertyPath.contains(".")) {
            // 尝试record class的访问方式：user.name()
            return objectName + "." + propertyPath + "()";
        }
        
        // 嵌套属性访问：user.address.city -> user.getAddress().getCity()
        String[] properties = propertyPath.split("\\.");
        StringBuilder access = new StringBuilder(objectName);
        
        for (String property : properties) {
            access.append(".").append(property).append("()");
        }
        
        return access.toString();
    }
    
    /**
     * SQL解析结果
     */
    public static class SqlParseResult {
        private final String processedSql;
        private final List<ParameterInfo> parameters;
        
        public SqlParseResult(String processedSql, List<ParameterInfo> parameters) {
            this.processedSql = processedSql;
            this.parameters = parameters;
        }
        
        public String getProcessedSql() {
            return processedSql;
        }
        
        public List<ParameterInfo> getParameters() {
            return parameters;
        }
        
        public boolean hasParameters() {
            return !parameters.isEmpty();
        }
    }
    
    /**
     * 参数信息
     */
    public static class ParameterInfo {
        private final int index;                    // PreparedStatement中的索引
        private final String expression;            // 原始表达式：#{user.name}
        private final String parameterName;         // 参数名：user
        private final String parameterType;         // 参数类型：com.example.User
        private final AccessType accessType;        // 访问类型：DIRECT/PROPERTY
        private final String propertyPath;          // 属性路径：name 或 address.city
        
        public ParameterInfo(int index, String expression, String parameterName, 
                           String parameterType, AccessType accessType, String propertyPath) {
            this.index = index;
            this.expression = expression;
            this.parameterName = parameterName;
            this.parameterType = parameterType;
            this.accessType = accessType;
            this.propertyPath = propertyPath;
        }
        
        // Getters
        public int getIndex() { return index; }
        public String getExpression() { return expression; }
        public String getParameterName() { return parameterName; }
        public String getParameterType() { return parameterType; }
        public AccessType getAccessType() { return accessType; }
        public String getPropertyPath() { return propertyPath; }
        
        /**
         * 参数访问类型
         */
        public enum AccessType {
            DIRECT,     // 直接参数：#{id}
            PROPERTY    // 对象属性：#{user.name}
        }
    }
}

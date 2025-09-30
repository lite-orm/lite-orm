package org.liteorm.compile;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于注解的动态SQL解析器
 * 
 * 物理职责：
 * 1. 解析MyBatis注解中的SQL内容（物理必需：注解语法解析）
 * 2. 识别注解中的<script>标签（物理必需：动态SQL检测）
 * 3. 处理#{param}参数语法（物理必需：参数绑定）
 * 4. 构建动态SQL的AST节点（物理必需：结构化表示）
 * 
 * 支持的注解：
 * - @Select, @Insert, @Update, @Delete
 * - 单个字符串或字符串数组
 * - <script>包装的动态SQL
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class AnnotationBasedDynamicSqlParser implements DynamicSqlParser {
    
    private static final Pattern SCRIPT_PATTERN = Pattern.compile("<script>(.*?)</script>", Pattern.DOTALL);
    private static final Pattern PARAM_PATTERN = Pattern.compile("#\\{([^}]+)\\}");
    
    @Override
    public SqlInfo parseSql(ExecutableElement method) {
        // 检查各种SQL注解
        String[] sqlStrings = extractSqlFromAnnotations(method);
        if (sqlStrings == null || sqlStrings.length == 0) {
            return null;
        }
        
        // 合并SQL字符串（支持注解数组）
        String fullSql = String.join(" ", sqlStrings).trim();
        
        // 确定SQL类型和来源类型
        SqlType sqlType = determineSqlType(method);
        SqlSourceType sourceType = determineSourceType(fullSql);
        
        // 检查是否为动态SQL
        boolean isDynamic = containsDynamicContent(fullSql);
        
        // 解析参数（从method.getParameters()获取）
        List<ParameterInfo> parameters = parseParameters(fullSql, method.getParameters());
        
        // 构建动态AST节点（如果需要）
        DynamicNode dynamicNode = null;
        if (isDynamic) {
            dynamicNode = buildDynamicNode(fullSql, method.getParameters());
        }
        
        return new SqlInfo(fullSql, sqlType, sourceType, isDynamic, parameters, dynamicNode);
    }
    
    @Override
    public boolean supports(ExecutableElement method) {
        return method.getAnnotation(Select.class) != null ||
               method.getAnnotation(Insert.class) != null ||
               method.getAnnotation(Update.class) != null ||
               method.getAnnotation(Delete.class) != null;
    }
    
    @Override
    public String getParserName() {
        return "AnnotationBasedDynamicSqlParser";
    }
    
    /**
     * 从注解中提取SQL字符串
     */
    private String[] extractSqlFromAnnotations(ExecutableElement method) {
        Select select = method.getAnnotation(Select.class);
        if (select != null) {
            return select.value();
        }
        
        Insert insert = method.getAnnotation(Insert.class);
        if (insert != null) {
            return insert.value();
        }
        
        Update update = method.getAnnotation(Update.class);
        if (update != null) {
            return update.value();
        }
        
        Delete delete = method.getAnnotation(Delete.class);
        if (delete != null) {
            return delete.value();
        }
        
        return null;
    }
    
    /**
     * 确定SQL类型
     */
    private SqlType determineSqlType(ExecutableElement method) {
        if (method.getAnnotation(Select.class) != null) return SqlType.SELECT;
        if (method.getAnnotation(Insert.class) != null) return SqlType.INSERT;
        if (method.getAnnotation(Update.class) != null) return SqlType.UPDATE;
        if (method.getAnnotation(Delete.class) != null) return SqlType.DELETE;
        return SqlType.SELECT; // 默认
    }
    
    /**
     * 确定SQL来源类型
     */
    private SqlSourceType determineSourceType(String sql) {
        if (SCRIPT_PATTERN.matcher(sql).find()) {
            return SqlSourceType.SCRIPT;
        }
        return SqlSourceType.ANNOTATION;
    }
    
    /**
     * 检查是否包含动态内容
     */
    private boolean containsDynamicContent(String sql) {
        return SCRIPT_PATTERN.matcher(sql).find() ||
               sql.contains("<if") || sql.contains("<foreach") || 
               sql.contains("<choose") || sql.contains("<where") ||
               sql.contains("<set") || sql.contains("<trim");
    }
    
    /**
     * 解析参数
     */
    private List<ParameterInfo> parseParameters(String sql, List<? extends VariableElement> methodParameters) {
        List<ParameterInfo> parameters = new ArrayList<>();
        Matcher matcher = PARAM_PATTERN.matcher(sql);
        
        while (matcher.find()) {
            String paramExpression = matcher.group(1).trim();
            ParameterInfo paramInfo = analyzeParameter(paramExpression, methodParameters);
            if (paramInfo != null) {
                parameters.add(paramInfo);
            }
        }
        
        return parameters;
    }
    
    /**
     * 分析单个参数表达式
     */
    private ParameterInfo analyzeParameter(String paramExpression, List<? extends VariableElement> methodParameters) {
        String[] parts = paramExpression.split("\\.");
        String rootParamName = parts[0];
        
        // 查找对应的方法参数
        VariableElement rootParam = methodParameters.stream()
            .filter(p -> p.getSimpleName().toString().equals(rootParamName))
            .findFirst()
            .orElse(null);
        
        if (rootParam == null) {
            return null; // 参数不存在
        }
        
        String accessCode;
        String typeName = rootParam.asType().toString();
        
        if (parts.length == 1) {
            // 简单参数：#{id}
            accessCode = rootParamName;
        } else {
            // 复杂参数：#{user.name}
            StringBuilder access = new StringBuilder(rootParamName);
            for (int i = 1; i < parts.length; i++) {
                access.append(".").append(parts[i]).append("()");
            }
            accessCode = access.toString();
            // TODO: 更精确的类型推断
        }
        
        return new ParameterInfo(paramExpression, accessCode, typeName);
    }
    
    /**
     * 构建动态AST节点
     */
    private DynamicNode buildDynamicNode(String sql, List<? extends VariableElement> methodParameters) {
        // 检查是否有<script>标签
        Matcher scriptMatcher = SCRIPT_PATTERN.matcher(sql);
        if (scriptMatcher.find()) {
            String dynamicContent = scriptMatcher.group(1).trim();
            return parseDynamicContent(dynamicContent);
        }
        
        // 直接包含动态标签
        if (containsDynamicContent(sql)) {
            return parseDynamicContent(sql);
        }
        
        // 静态SQL，返回文本节点
        return new TextNode(sql);
    }
    
    /**
     * 解析动态内容为AST节点
     */
    private DynamicNode parseDynamicContent(String content) {
        // 简化实现：目前只处理基本情况
        // TODO: 完整的XML解析实现，构建完整的AST树
        
        if (content.contains("<if")) {
            // 简单的IF节点处理
            return new IfNode("simplified_condition", List.of(new TextNode(content)));
        }
        
        if (content.contains("<foreach")) {
            // 简单的FOREACH节点处理
            return new ForeachNode("collection", "item", ",", "(", ")", 
                                 List.of(new TextNode(content)));
        }
        
        // 默认返回文本节点
        return new TextNode(content);
    }
}

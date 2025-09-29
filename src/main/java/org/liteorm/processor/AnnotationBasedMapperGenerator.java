package org.liteorm.processor;

import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.List;

/**
 * 基于注解的Mapper生成器
 * <p>
 * 物理必需性分析：
 * 1. 解析@Select/@Insert/@Update/@Delete注解 - 获取SQL（物理必需）
 * 2. 分析方法参数 - 生成类型安全的参数绑定（物理必需）
 * 3. 分析返回类型 - 生成硬编码的结果映射（物理必需）
 * 4. 调用SqlEngine - 执行SQL任务（物理必需）
 * </p>
 * 设计原则：
 * - 每一行生成的代码都有明确的物理对应
 * - 零反射：所有类型转换都是硬编码的
 * - 类型安全：编译期确定所有参数和返回值类型
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class AnnotationBasedMapperGenerator {
    
    private final Filer filer;
    private final Messager messager;
    private final Elements elementUtils;
    private final Types typeUtils;
    
    public AnnotationBasedMapperGenerator(Filer filer, Messager messager, 
                                        Elements elementUtils, Types typeUtils) {
        this.filer = filer;
        this.messager = messager;
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
    }
    
    public String generate(TypeElement typeElement) {
        String packageName = elementUtils.getPackageOf(typeElement).getQualifiedName().toString();
        String interfaceName = typeElement.getSimpleName().toString();
        String className = interfaceName + "Impl";
        
        StringBuilder code = new StringBuilder();
        
        // 包声明（物理必需：Java文件结构）
        code.append("package ").append(packageName).append(";\n\n");
        
        // 导入（物理必需：使用的类）
        code.append("import org.liteorm.*;\n");
        code.append("import java.util.List;\n");
        code.append("import java.util.ArrayList;\n\n");
        
        // 类声明（物理必需：实现接口）
        code.append("/**\n");
        code.append(" * Generated MapperImpl - Zero Reflection\n");
        code.append(" * 编译期生成的硬编码实现，无任何反射调用\n");
        code.append(" */\n");
        code.append("public class ").append(className).append(" implements ").append(interfaceName).append(" {\n\n");
        
        // 字段（物理必需：SQL执行引擎）
        code.append("    private final SqlEngine sqlEngine;\n\n");
        
        // 构造函数（物理必需：依赖注入）
        code.append("    public ").append(className).append("(ConnectionManager connectionManager) {\n");
        code.append("        this.sqlEngine = new DefaultSqlEngine(connectionManager);\n");
        code.append("    }\n\n");
        
        // 生成方法实现（物理必需：接口契约）
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement instanceof ExecutableElement) {
                ExecutableElement method = (ExecutableElement) enclosedElement;
                code.append(generateMethodImpl(method));
            }
        }
        
        code.append("}\n");
        
        return code.toString();
    }
    
    /**
     * 生成方法实现 - 基于物理必需性
     * 
     * 物理步骤：
     * 1. 提取SQL（物理事实：注解中的字符串）
     * 2. 分析参数（物理事实：方法签名）
     * 3. 创建SqlTask（物理必需：任务封装）
     * 4. 调用SqlEngine（物理必需：执行SQL）
     * 5. 处理结果（物理必需：类型转换）
     */
    private String generateMethodImpl(ExecutableElement method) {
        StringBuilder methodCode = new StringBuilder();
        
        String methodName = method.getSimpleName().toString();
        String returnType = method.getReturnType().toString();
        List<? extends VariableElement> parameters = method.getParameters();
        
        // 第1步：提取SQL（物理事实）
        SqlInfo sqlInfo = extractSqlInfo(method);
        if (sqlInfo == null) {
            return generateNoSqlMethod(methodName, returnType, parameters);
        }
        
        // 生成方法签名（物理必需：Java语法）
        methodCode.append("    @Override\n");
        methodCode.append("    public ").append(returnType).append(" ").append(methodName).append("(");
        methodCode.append(generateParameterList(parameters));
        methodCode.append(") {\n");
        
        // 第2步：硬编码SQL（物理事实：编译期确定）
        methodCode.append("        // 硬编码SQL - 编译期确定，零解析开销\n");
        methodCode.append("        String sql = \"").append(sqlInfo.sql).append("\";\n");
        
        // 第3步：类型安全的参数绑定（物理必需：防SQL注入）
        methodCode.append("        // 类型安全的参数绑定 - 零反射\n");
        methodCode.append(generateParameterBinding(parameters));
        
        // 第4步：创建SqlTask（物理必需：任务封装）
        methodCode.append("        // 创建SQL执行任务\n");
        methodCode.append("        SqlTask task = new SqlTask(sql, params, SqlTask.SqlType.")
                  .append(sqlInfo.type).append(", ").append(sqlInfo.requiresTransaction).append(");\n");
        
        // 第5步：执行SQL（物理必需：核心操作）
        methodCode.append("        // 执行SQL - 通过责任链处理\n");
        methodCode.append("        SqlResult result = sqlEngine.execute(task);\n");
        
        // 第6步：结果处理（物理必需：类型转换）
        methodCode.append("        // 硬编码结果映射 - 零反射\n");
        methodCode.append(generateResultMapping(returnType, sqlInfo.type));
        
        methodCode.append("    }\n\n");
        
        return methodCode.toString();
    }
    
    /**
     * SQL信息封装
     */
    private static class SqlInfo {
        String sql;
        String type;
        boolean requiresTransaction;
        
        SqlInfo(String sql, String type, boolean requiresTransaction) {
            this.sql = sql;
            this.type = type;
            this.requiresTransaction = requiresTransaction;
        }
    }
    
    /**
     * 提取SQL信息 - 基于物理事实：注解中的字符串
     */
    private SqlInfo extractSqlInfo(ExecutableElement method) {
        // 检查@Select注解
        Select select = method.getAnnotation(Select.class);
        if (select != null && select.value().length > 0) {
            return new SqlInfo(select.value()[0], "SELECT", false);
        }
        
        // 检查@Insert注解
        Insert insert = method.getAnnotation(Insert.class);
        if (insert != null && insert.value().length > 0) {
            return new SqlInfo(insert.value()[0], "INSERT", true);
        }
        
        // 检查@Update注解
        Update update = method.getAnnotation(Update.class);
        if (update != null && update.value().length > 0) {
            return new SqlInfo(update.value()[0], "UPDATE", true);
        }
        
        // 检查@Delete注解
        Delete delete = method.getAnnotation(Delete.class);
        if (delete != null && delete.value().length > 0) {
            return new SqlInfo(delete.value()[0], "DELETE", true);
        }
        
        return null;
    }
    
    /**
     * 生成无SQL注解的方法实现
     */
    private String generateNoSqlMethod(String methodName, String returnType, 
                                     List<? extends VariableElement> parameters) {
        StringBuilder code = new StringBuilder();
        code.append("    @Override\n");
        code.append("    public ").append(returnType).append(" ").append(methodName).append("(");
        code.append(generateParameterList(parameters));
        code.append(") {\n");
        code.append("        throw new UnsupportedOperationException(\"No SQL annotation found for method: ")
            .append(methodName).append("\");\n");
        code.append("    }\n\n");
        return code.toString();
    }
    
    /**
     * 生成参数列表 - 类型安全
     */
    private String generateParameterList(List<? extends VariableElement> parameters) {
        if (parameters.isEmpty()) {
            return "";
        }
        
        StringBuilder paramList = new StringBuilder();
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement param = parameters.get(i);
            if (i > 0) {
                paramList.append(", ");
            }
            paramList.append(param.asType().toString())
                    .append(" ")
                    .append(param.getSimpleName().toString());
        }
        return paramList.toString();
    }
    
    /**
     * 生成参数绑定代码 - 硬编码，零反射
     */
    private String generateParameterBinding(List<? extends VariableElement> parameters) {
        if (parameters.isEmpty()) {
            return "        Object[] params = new Object[0];\n";
        }
        
        StringBuilder code = new StringBuilder();
        code.append("        Object[] params = new Object[").append(parameters.size()).append("];\n");
        
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement param = parameters.get(i);
            String paramName = param.getSimpleName().toString();
            code.append("        params[").append(i).append("] = ").append(paramName).append(";\n");
        }
        
        return code.toString();
    }
    
    /**
     * 生成结果映射代码 - 硬编码，零反射
     */
    private String generateResultMapping(String returnType, String sqlType) {
        StringBuilder code = new StringBuilder();
        
        if ("SELECT".equals(sqlType)) {
            if (returnType.contains("List")) {
                // List返回类型
                code.append("        if (result.hasError()) {\n");
                code.append("            throw new RuntimeException(result.getException());\n");
                code.append("        }\n");
                code.append("        // TODO: 实现具体的List<T>映射\n");
                code.append("        return new ArrayList<>();\n");
            } else if (!"void".equals(returnType)) {
                // 单个对象返回类型
                code.append("        if (result.hasError()) {\n");
                code.append("            throw new RuntimeException(result.getException());\n");
                code.append("        }\n");
                code.append("        // TODO: 实现具体的对象映射\n");
                code.append("        return null;\n");
            }
        } else {
            // INSERT/UPDATE/DELETE返回int
            code.append("        if (result.hasError()) {\n");
            code.append("            throw new RuntimeException(result.getException());\n");
            code.append("        }\n");
            code.append("        return result.getUpdateCount();\n");
        }
        
        return code.toString();
    }
    
    /**
     * 检查是否支持该类型元素
     */
    public boolean supports(TypeElement typeElement) {
        return typeElement.getAnnotation(org.apache.ibatis.annotations.Mapper.class) != null;
    }
}

package org.liteorm.compile;

import org.liteorm.api.ExecutionPlan;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 编译管道 - 串联解析→渲染→代码生成的主线流程
 * 
 * 职责：
 * 1. 协调SQL解析器和代码生成器
 * 2. 管理解析器优先级
 * 3. 提供统一的编译接口
 * 
 * @author lite-orm
 * @since 2024/10/01
 */
public class CompilePipeline {
    
    private final List<SqlContentParser> sqlParsers;
    private final CodeGenerator codeGenerator;
    private final SqlParameterParser parameterParser;
    private final Elements elementUtils;
    private final Types typeUtils;
    
    public CompilePipeline(Elements elementUtils, Types typeUtils) {
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
        
        // 初始化解析器（按优先级排序）
        this.sqlParsers = Arrays.asList(
            new XmlBasedSqlParser(),           // 优先级最高：XML覆盖注解
            new AnnotationBasedSqlParser()     // 注解解析器
        );
        
        // 初始化代码生成器和参数解析器
        this.codeGenerator = new FreemarkerCodeGenerator();
        this.parameterParser = new SqlParameterParser();
    }
    
    /**
     * 编译Mapper接口
     * 
     * @param mapperInterface Mapper接口
     * @return 生成的Java代码
     * @throws CompileException 编译异常
     */
    public String compileMapper(TypeElement mapperInterface) throws CompileException {
        try {
            MapperCompilationModel compilationModel = buildCompilationModel(mapperInterface);

            if (compilationModel.methods().isEmpty()) {
                throw new CompileException("No SQL methods found in interface " + 
                    mapperInterface.getQualifiedName());
            }
            
            // 2. 生成代码
            return codeGenerator.generateMapperImpl(mapperInterface, compilationModel, elementUtils, typeUtils);
            
        } catch (Exception e) {
            throw new CompileException("Failed to compile mapper: " + 
                mapperInterface.getQualifiedName(), e);
        }
    }

    public MapperCompilationModel buildCompilationModel(TypeElement mapperInterface) throws CompileException {
        List<MapperCompilationModel.MethodModel> methods = analyzeInterfaceMethods(mapperInterface);
        String packageName = elementUtils.getPackageOf(mapperInterface).getQualifiedName().toString();
        String interfaceName = mapperInterface.getSimpleName().toString();
        return new MapperCompilationModel(
            packageName,
            interfaceName,
            interfaceName + "Impl",
            mapperInterface.getQualifiedName().toString(),
            methods
        );
    }
    
    /**
     * 检查是否支持该接口
     */
    public boolean supports(TypeElement mapperInterface) {
        // 检查是否有@Mapper注解
        if (mapperInterface.getAnnotation(org.apache.ibatis.annotations.Mapper.class) != null) {
            return true;
        }
        
        // 检查是否有任何方法包含SQL注解
        return mapperInterface.getEnclosedElements().stream()
            .anyMatch(element -> element instanceof ExecutableElement &&
                sqlParsers.stream().anyMatch(parser -> parser.supports((ExecutableElement) element)));
    }
    
    /**
     * 分析接口方法
     */
    private List<MapperCompilationModel.MethodModel> analyzeInterfaceMethods(TypeElement mapperInterface)
            throws CompileException {
        List<MapperCompilationModel.MethodModel> methodInfos = new ArrayList<>();
        
        for (var element : mapperInterface.getEnclosedElements()) {
            if (element instanceof ExecutableElement method) {
                MapperCompilationModel.MethodModel methodInfo = analyzeMethod(mapperInterface, method);
                if (methodInfo != null) {
                    methodInfos.add(methodInfo);
                }
            }
        }
        
        return methodInfos;
    }
    
    /**
     * 分析单个方法
     */
    private MapperCompilationModel.MethodModel analyzeMethod(TypeElement mapperInterface, ExecutableElement method)
            throws CompileException {
        validateMethodSignature(method);

        // 1. 解析SQL内容
        SqlContentParser.SqlParseResult sqlInfo = null;
        for (SqlContentParser parser : sqlParsers) {
            if (parser.supports(method)) {
                try {
                    sqlInfo = parser.parseSql(method);
                    if (sqlInfo != null) {
                        break;
                    }
                } catch (Exception e) {
                    throw new CompileException("Failed to parse " + parser.getParserName() + " for " +
                        mapperInterface.getQualifiedName() + "#" + method.getSimpleName() + ": " + e.getMessage(), e);
                }
            }
        }
        
        if (sqlInfo == null) {
            return null; // 跳过没有SQL的方法
        }
        
        // 2. 生成标准化参数模型和绑定顺序
        List<SqlParameterParser.MethodParameter> methodParameters =
            parameterParser.describeMethodParameters(method.getParameters());
        SqlParameterParser.SqlParseResult parameterResult = new SqlParameterParser.SqlParseResult(sqlInfo.sqlTemplate(), List.of());
        if (!sqlInfo.isDynamic()) {
            try {
                parameterResult = parameterParser.parseSql(sqlInfo.sqlTemplate(), methodParameters);
            } catch (Exception e) {
                throw new CompileException("Failed to resolve SQL parameters for " +
                    mapperInterface.getQualifiedName() + "#" + method.getSimpleName() + ": " + e.getMessage(), e);
            }
        }

        // 3. 构建方法信息
        String methodName = method.getSimpleName().toString();
        String returnType = method.getReturnType().toString();
        String parameterList = buildParameterList(method);
        String resultMappingCode = generateResultMapping(returnType);

        return new MapperCompilationModel.MethodModel(
            methodName,
            returnType,
            parameterList,
            "build" + Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1) + "ExecutionPlan",
            mapperInterface.getQualifiedName() + "." + methodName,
            mapStatementType(sqlInfo.sqlType()),
            mapSourceType(sqlInfo.sourceType()),
            sqlInfo.isDynamic() ? sqlInfo.sqlTemplate() : parameterResult.processedSql(),
            sqlInfo.isDynamic(),
            requiresTransaction(sqlInfo.sqlType()),
            returnType,
            resultMappingCode,
            methodParameters,
            parameterResult.bindings(),
            sqlInfo.astNode()
        );
    }

    private void validateMethodSignature(ExecutableElement method) throws CompileException {
        if (method.isDefault()) {
            throw new CompileException("Default mapper methods are not supported: " + method.getSimpleName());
        }
        if (method.isVarArgs()) {
            throw new CompileException("Varargs mapper methods are not supported: " + method.getSimpleName());
        }
        if (method.getModifiers().contains(javax.lang.model.element.Modifier.STATIC)) {
            throw new CompileException("Static mapper methods are not supported: " + method.getSimpleName());
        }
    }

    private ExecutionPlan.StatementType mapStatementType(SqlContentParser.SqlType sqlType) {
        return switch (sqlType) {
            case SELECT -> ExecutionPlan.StatementType.SELECT;
            case INSERT -> ExecutionPlan.StatementType.INSERT;
            case UPDATE -> ExecutionPlan.StatementType.UPDATE;
            case DELETE -> ExecutionPlan.StatementType.DELETE;
            case BATCH -> ExecutionPlan.StatementType.BATCH;
        };
    }

    private ExecutionPlan.SqlSource mapSourceType(SqlContentParser.SqlSourceType sourceType) {
        return switch (sourceType) {
            case XML -> ExecutionPlan.SqlSource.XML;
            case ANNOTATION -> ExecutionPlan.SqlSource.ANNOTATION;
            case SCRIPT -> ExecutionPlan.SqlSource.SCRIPT;
        };
    }

    private boolean requiresTransaction(SqlContentParser.SqlType sqlType) {
        return sqlType != SqlContentParser.SqlType.SELECT;
    }
    
    /**
     * 构建参数列表字符串
     */
    private String buildParameterList(ExecutableElement method) {
        List<String> params = new ArrayList<>();
        for (var param : method.getParameters()) {
            String paramType = param.asType().toString();
            String paramName = param.getSimpleName().toString();
            params.add(paramType + " " + paramName);
        }
        return String.join(", ", params);
    }
    
    /**
     * 生成结果映射代码
     */
    private String generateResultMapping(String returnType) {
        // 简化实现，完整版本需要更复杂的类型分析
        if (returnType.contains("List<")) {
            String elementType = extractListElementType(returnType);
            return generateSingleMapping(elementType);
        } else if (!returnType.equals("void")) {
            return generateSingleMapping(returnType);
        } else {
            return ""; // void方法无需结果映射
        }
    }
    
    /**
     * 提取List元素类型
     */
    private String extractListElementType(String listType) {
        int start = listType.indexOf('<') + 1;
        int end = listType.lastIndexOf('>');
        return listType.substring(start, end);
    }
    
    /**
     * 生成单对象映射代码
     */
    private String generateSingleMapping(String objectType) {
        try {
            var typeElement = elementUtils.getTypeElement(objectType);
            if (typeElement == null) {
                // 基本类型或包装类型，直接转换
                return "(" + objectType + ")row[0]";
            }
            
            // 检查是否是record class
            if (typeElement.getKind() == javax.lang.model.element.ElementKind.RECORD) {
                return generateRecordMapping(typeElement, objectType);
            }
            
            // 普通类：尝试使用全参构造器
            return generateClassMapping(typeElement, objectType);
            
        } catch (Exception e) {
            return "/* 类型映射失败: " + objectType + " */ null";
        }
    }
    
    /**
     * 生成record class映射代码（零反射）
     */
    private String generateRecordMapping(javax.lang.model.element.TypeElement typeElement, String objectType) {
        // 获取record components
        var recordComponents = typeElement.getRecordComponents();
        
        StringBuilder mapping = new StringBuilder("new " + objectType + "(");
        for (int i = 0; i < recordComponents.size(); i++) {
            var component = recordComponents.get(i);
            String componentType = component.asType().toString();
            
            if (i > 0) {
                mapping.append(", ");
            }
            
            // 生成硬编码的类型转换：(Long)row[0], (String)row[1], ...
            mapping.append("(").append(componentType).append(")row[").append(i).append("]");
        }
        mapping.append(")");
        
        return mapping.toString();
    }
    
    /**
     * 生成普通类映射代码
     */
    private String generateClassMapping(javax.lang.model.element.TypeElement typeElement, String objectType) {
        // 简化实现：查找所有字段，生成构造器调用
        // 这里假设有一个匹配字段顺序的构造器
        var fields = new ArrayList<javax.lang.model.element.VariableElement>();
        for (var enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind() == javax.lang.model.element.ElementKind.FIELD) {
                fields.add((javax.lang.model.element.VariableElement) enclosed);
            }
        }
        
        if (fields.isEmpty()) {
            return "new " + objectType + "()";
        }
        
        StringBuilder mapping = new StringBuilder("new " + objectType + "(");
        for (int i = 0; i < fields.size(); i++) {
            var field = fields.get(i);
            String fieldType = field.asType().toString();
            
            if (i > 0) {
                mapping.append(", ");
            }
            
            mapping.append("(").append(fieldType).append(")row[").append(i).append("]");
        }
        mapping.append(")");
        
        return mapping.toString();
    }
    
    /**
     * 编译异常
     */
    public static class CompileException extends Exception {
        public CompileException(String message) {
            super(message);
        }
        
        public CompileException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

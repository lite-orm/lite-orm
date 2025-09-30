package org.liteorm.compile;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Mapper实现类生成器 - 基于第一性原理的统一生成架构
 * 
 * 物理职责：
 * 1. 协调SQL解析器和代码渲染器（物理必需：流程编排）
 * 2. 分析接口方法结构（物理必需：理解用户定义）
 * 3. 生成类型安全的结果映射（物理必需：零反射实现）
 * 4. 输出完整的MapperImpl类（物理必需：可编译代码）
 * 
 * 架构设计：
 * 解析阶段：SqlContentParser -> SqlInfo
 * 渲染阶段：CodeRenderer -> Java代码
 * 
 * 解析器优先级：
 * 1. XML文件（如果存在）
 * 2. 注解中的SQL
 * 3. 自定义解析器
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class MapperImplGenerator {
    
    private final List<DynamicSqlParser> sqlParsers;
    private final CodeRenderer codeRenderer;
    private final Elements elementUtils;
    private final Types typeUtils;
    
    public MapperImplGenerator(Elements elementUtils, Types typeUtils) {
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
        
        // 初始化解析器（按优先级排序）
        this.sqlParsers = Arrays.asList(
            // new XmlBasedDynamicSqlParser(),  // 优先级最高：XML覆盖注解
            new AnnotationBasedDynamicSqlParser() // 注解解析器
        );
        
        // 初始化渲染器
        this.codeRenderer = new TemplateBasedCodeRenderer();
    }
    
    /**
     * 生成Mapper实现类
     * 
     * @param mapperInterface Mapper接口
     * @return 生成的Java代码
     * @throws GenerationException 生成异常
     */
    public String generateMapperImpl(TypeElement mapperInterface) throws GenerationException {
        try {
            // 1. 分析接口方法
            List<CodeRenderer.MethodInfo> methodInfos = analyzeInterfaceMethods(mapperInterface);
            
            // 2. 生成代码
            return codeRenderer.renderMapperImpl(mapperInterface, methodInfos, elementUtils, typeUtils);
            
        } catch (Exception e) {
            throw new GenerationException("Failed to generate mapper implementation for " + 
                mapperInterface.getQualifiedName(), e);
        }
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
    private List<CodeRenderer.MethodInfo> analyzeInterfaceMethods(TypeElement mapperInterface) 
            throws GenerationException {
        List<CodeRenderer.MethodInfo> methodInfos = new ArrayList<>();
        
        for (var element : mapperInterface.getEnclosedElements()) {
            if (element instanceof ExecutableElement method) {
                CodeRenderer.MethodInfo methodInfo = analyzeMethod(method);
                if (methodInfo != null) {
                    methodInfos.add(methodInfo);
                }
            }
        }
        
        if (methodInfos.isEmpty()) {
            throw new GenerationException("No SQL methods found in interface " + 
                mapperInterface.getQualifiedName());
        }
        
        return methodInfos;
    }
    
    /**
     * 分析单个方法
     */
    private CodeRenderer.MethodInfo analyzeMethod(ExecutableElement method) throws GenerationException {
        // 1. 解析SQL内容
        DynamicSqlParser.SqlInfo sqlInfo = null;
        for (DynamicSqlParser parser : sqlParsers) {
            if (parser.supports(method)) {
                sqlInfo = parser.parseSql(method);
                if (sqlInfo != null) {
                    break;
                }
            }
        }
        
        if (sqlInfo == null) {
            return null; // 跳过没有SQL的方法
        }
        
        // 2. 构建方法信息
        String methodName = method.getSimpleName().toString();
        String returnType = method.getReturnType().toString();
        String parameterList = buildParameterList(method);
        String resultMappingCode = generateResultMapping(returnType);
        
        return new CodeRenderer.MethodInfo(methodName, returnType, parameterList, sqlInfo, resultMappingCode);
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
        // 使用TypeInferenceEngine生成硬编码映射
        TypeInferenceEngine inferenceEngine = new TypeInferenceEngine(elementUtils, typeUtils);
        
        try {
            // 简化实现，完整版本需要更复杂的类型分析
            if (returnType.contains("List<")) {
                String elementType = extractListElementType(returnType);
                return generateListMapping(elementType, inferenceEngine);
            } else if (!returnType.equals("void")) {
                return generateSingleMapping(returnType, inferenceEngine);
            } else {
                return ""; // void方法无需结果映射
            }
        } catch (Exception e) {
            return "/* 结果映射生成失败: " + e.getMessage() + " */ null";
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
     * 生成List映射代码
     */
    private String generateListMapping(String elementType, TypeInferenceEngine inferenceEngine) {
        return "resultList.add(" + generateSingleMapping(elementType, inferenceEngine) + ");";
    }
    
    /**
     * 生成单对象映射代码
     */
    private String generateSingleMapping(String objectType, TypeInferenceEngine inferenceEngine) {
        try {
            var typeElement = elementUtils.getTypeElement(objectType);
            if (typeElement == null) {
                return "(" + objectType + ")row[0]"; // 简单类型处理
            }
            
            var typeInfo = inferenceEngine.analyzeType(typeElement.asType());
            return inferenceEngine.generateConstructorCode(typeInfo, "row");
            
        } catch (Exception e) {
            return "/* 类型映射失败: " + objectType + " */ null";
        }
    }
    
    /**
     * 生成异常
     */
    public static class GenerationException extends Exception {
        public GenerationException(String message) {
            super(message);
        }
        
        public GenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

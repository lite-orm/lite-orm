package org.liteorm.compile;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于FreeMarker模板的代码渲染器
 * 
 * 物理职责：
 * 1. 使用FreeMarker模板渲染Java代码（物理必需：代码生成）
 * 2. 处理模板数据模型构建（物理必需：模板输入）
 * 3. 生成格式化的Java源码（物理必需：可读性）
 * 4. 处理模板异常和错误（物理必需：健壮性）
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class TemplateBasedCodeRenderer implements CodeRenderer {
    
    private final Configuration freemarkerConfig;
    
    public TemplateBasedCodeRenderer() {
        this.freemarkerConfig = new Configuration(Configuration.VERSION_2_3_32);
        freemarkerConfig.setClassForTemplateLoading(TemplateBasedCodeRenderer.class, "/templates");
        freemarkerConfig.setDefaultEncoding("UTF-8");
        freemarkerConfig.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        freemarkerConfig.setLogTemplateExceptions(false);
        freemarkerConfig.setWrapUncheckedExceptions(true);
    }
    
    @Override
    public String renderMapperImpl(TypeElement mapperInterface, 
                                  List<MethodInfo> methodInfos,
                                  Elements elementUtils, 
                                  Types typeUtils) throws RenderException {
        try {
            // 构建模板数据模型
            Map<String, Object> dataModel = buildDataModel(mapperInterface, methodInfos, elementUtils);
            
            // 获取模板
            Template template = freemarkerConfig.getTemplate("mapper-impl.ftl");
            
            // 渲染模板
            StringWriter writer = new StringWriter();
            template.process(dataModel, writer);
            
            return writer.toString();
            
        } catch (Exception e) {
            throw new RenderException("Failed to render mapper implementation", e);
        }
    }
    
    @Override
    public String getRendererName() {
        return "TemplateBasedCodeRenderer";
    }
    
    /**
     * 构建模板数据模型
     */
    private Map<String, Object> buildDataModel(TypeElement mapperInterface, 
                                             List<MethodInfo> methodInfos,
                                             Elements elementUtils) {
        Map<String, Object> dataModel = new HashMap<>();
        
        // 基本信息
        String packageName = elementUtils.getPackageOf(mapperInterface).getQualifiedName().toString();
        String interfaceName = mapperInterface.getSimpleName().toString();
        String className = interfaceName + "Impl";
        
        dataModel.put("packageName", packageName);
        dataModel.put("interfaceName", interfaceName);
        dataModel.put("className", className);
        dataModel.put("methods", buildMethodData(methodInfos));
        
        return dataModel;
    }
    
    /**
     * 构建方法数据
     */
    private List<Map<String, Object>> buildMethodData(List<MethodInfo> methodInfos) {
        return methodInfos.stream().map(this::buildSingleMethodData).toList();
    }
    
    /**
     * 构建单个方法的数据
     */
    private Map<String, Object> buildSingleMethodData(MethodInfo methodInfo) {
        Map<String, Object> methodData = new HashMap<>();
        
        methodData.put("name", methodInfo.getMethodName());
        methodData.put("returnType", methodInfo.getReturnType());
        methodData.put("parameterList", methodInfo.getParameterList());
        methodData.put("resultMappingCode", methodInfo.getResultMappingCode());
        
        // SQL信息
        DynamicSqlParser.SqlInfo sqlInfo = methodInfo.getSqlInfo();
        if (sqlInfo != null) {
            Map<String, Object> sqlData = new HashMap<>();
            sqlData.put("template", sqlInfo.getSqlTemplate());
            sqlData.put("type", sqlInfo.getSqlType().name());
            sqlData.put("isDynamic", sqlInfo.isDynamic());
            sqlData.put("sourceType", sqlInfo.getSourceType().name());
            sqlData.put("dynamicNode", sqlInfo.getDynamicNode());
            
            // 参数信息
            List<Map<String, Object>> paramData = sqlInfo.getParameters().stream()
                .map(param -> {
                    Map<String, Object> p = new HashMap<>();
                    p.put("name", param.getName());
                    p.put("accessCode", param.getAccessCode());
                    p.put("typeName", param.getTypeName());
                    return p;
                }).toList();
            sqlData.put("parameters", paramData);
            
            methodData.put("sqlInfo", sqlData);
        }
        
        return methodData;
    }
}

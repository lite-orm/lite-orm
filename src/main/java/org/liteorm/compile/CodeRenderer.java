package org.liteorm.compile;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.List;

/**
 * 代码渲染器接口 - 基于第一性原理的代码生成抽象
 * 
 * 物理职责：
 * 1. 接收标准化的SQL信息（物理必需：统一输入）
 * 2. 生成硬编码的Java代码（物理必需：零反射实现）
 * 3. 处理类型映射和转换（物理必需：类型安全）
 * 4. 输出完整的MapperImpl类（物理必需：可编译代码）
 * 
 * 设计原则：
 * - 渲染和解析分离：只负责代码生成，不关心输入来源
 * - 模板化生成：支持多种代码生成策略
 * - 类型安全：编译期确定所有类型转换
 * - 可扩展：支持自定义渲染器
 * 
 * 支持的渲染策略：
 * - FreeMarker模板渲染
 * - 硬编码字符串拼接
 * - 自定义模板引擎
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public interface CodeRenderer {
    
    /**
     * 渲染Mapper实现类
     * 
     * @param mapperInterface Mapper接口
     * @param methodInfos 方法SQL信息列表
     * @param elementUtils 元素工具
     * @param typeUtils 类型工具
     * @return 生成的Java代码
     * @throws RenderException 渲染异常
     */
    String renderMapperImpl(TypeElement mapperInterface, 
                           List<MethodInfo> methodInfos,
                           Elements elementUtils, 
                           Types typeUtils) throws RenderException;
    
    /**
     * 获取渲染器名称
     * 
     * @return 渲染器名称
     */
    String getRendererName();
    
    /**
     * 方法信息封装
     */
    class MethodInfo {
        private final String methodName;                     // 方法名
        private final String returnType;                    // 返回类型
        private final String parameterList;                 // 参数列表
        private final DynamicSqlParser.SqlInfo sqlInfo;     // SQL信息
        private final String resultMappingCode;             // 结果映射代码
        
        public MethodInfo(String methodName, String returnType, String parameterList,
                         DynamicSqlParser.SqlInfo sqlInfo, String resultMappingCode) {
            this.methodName = methodName;
            this.returnType = returnType;
            this.parameterList = parameterList;
            this.sqlInfo = sqlInfo;
            this.resultMappingCode = resultMappingCode;
        }
        
        // Getters
        public String getMethodName() { return methodName; }
        public String getReturnType() { return returnType; }
        public String getParameterList() { return parameterList; }
        public DynamicSqlParser.SqlInfo getSqlInfo() { return sqlInfo; }
        public String getResultMappingCode() { return resultMappingCode; }
    }
    
    /**
     * 渲染异常
     */
    class RenderException extends Exception {
        public RenderException(String message) {
            super(message);
        }
        
        public RenderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

package org.liteorm.compile;

import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * 代码生成器接口 - 将AST转换为Java代码
 * 
 * 职责：
 * 1. 将AST节点转换为Java代码逻辑
 * 2. 生成参数绑定代码
 * 3. 生成结果映射代码
 * 4. 使用FTL模板生成完整的MapperImpl类
 * 
 * @author lite-orm
 * @since 2024/10/01
 */
interface CodeGenerator {
    
    /**
     * 生成Mapper实现类
     * 
     * @param mapperInterface Mapper接口
     * @param compilationModel Mapper标准编译模型
     * @param elementUtils 元素工具
     * @param typeUtils 类型工具
     * @return 生成的Java代码
     * @throws GenerationException 生成异常
     */
    String generateMapperImpl(TypeElement mapperInterface, MapperCompilationModel compilationModel,
                             Elements elementUtils, Types typeUtils) throws GenerationException;
    
    /**
     * 生成单个方法的实现代码
     * 
     * @param methodModel 方法信息
     * @return 方法实现代码
     * @throws GenerationException 生成异常
     */
    String generateMethodImpl(MapperCompilationModel.MethodModel methodModel) throws GenerationException;
    
    /**
     * 生成异常
     */
    class GenerationException extends Exception {
        public GenerationException(String message) {
            super(message);
        }
        
        public GenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

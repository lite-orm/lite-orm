package org.liteorm.compile;

import org.liteorm.annotation.Mapper;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * 重新设计的LiteORM注解处理器
 * 
 * 核心理念：
 * - 生成零反射的纯Java代码
 * - 硬编码SQL和结果映射
 * - 最小化运行时依赖
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
@SupportedAnnotationTypes({
    "org.liteorm.annotation.Mapper"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class LiteOrmProcessor extends AbstractProcessor {
    
    private Filer filer;
    private Messager messager;
    private Elements elementUtils;
    private Types typeUtils;
    
    private CompilePipeline compilePipeline;
    
    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.filer = processingEnv.getFiler();
        this.messager = processingEnv.getMessager();
        this.elementUtils = processingEnv.getElementUtils();
        this.typeUtils = processingEnv.getTypeUtils();
        
        // 初始化编译管道
        this.compilePipeline = new CompilePipeline(elementUtils, typeUtils, messager, filer);
        
        messager.printMessage(Diagnostic.Kind.NOTE, "LiteORM Processor initialized");
    }
    
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (annotations.isEmpty()) {
            return false;
        }
        
        messager.printMessage(Diagnostic.Kind.NOTE, "LiteORM Processing started...");
        
        try {
            // 只处理@Mapper注解 - 生成零反射的纯Java实现
            processMapperAnnotations(roundEnv);
            
            messager.printMessage(Diagnostic.Kind.NOTE, "LiteORM Processing completed successfully");
            
        } catch (Exception e) {
            messager.printMessage(Diagnostic.Kind.ERROR, 
                "Error during processing: " + e.getMessage());
            e.printStackTrace();
        }
        
        return true;
    }
    
    
    private void processMapperAnnotations(RoundEnvironment roundEnv) throws IOException {
        Set<? extends Element> mapperElements = roundEnv.getElementsAnnotatedWith(Mapper.class);
        
        for (Element element : mapperElements) {
            if (element instanceof TypeElement) {
                TypeElement typeElement = (TypeElement) element;
                messager.printMessage(Diagnostic.Kind.NOTE, 
                    "Processing Mapper: " + typeElement.getQualifiedName());
                
                // 生成零反射的MapperImpl
                generateZeroReflectionMapperImpl(typeElement);
            }
        }
    }
    
    /**
     * 生成零反射的MapperImpl
     */
    private void generateZeroReflectionMapperImpl(TypeElement mapperInterface) throws IOException {
        try {
            // 检查是否支持该接口
            if (!compilePipeline.supports(mapperInterface)) {
                messager.printMessage(Diagnostic.Kind.WARNING,
                    "Skipping unsupported interface: " + mapperInterface.getQualifiedName());
                return;
            }
            
            String packageName = elementUtils.getPackageOf(mapperInterface).getQualifiedName().toString();
            String className = mapperInterface.getSimpleName() + "Impl";
            String qualifiedClassName = packageName + "." + className;
            
            // 编译生成代码
            String javaCode = compilePipeline.compileMapper(mapperInterface);
            
            // 写入文件
            JavaFileObject builderFile = filer.createSourceFile(qualifiedClassName);
            try (Writer writer = builderFile.openWriter()) {
                writer.write(javaCode);
            }
            
            messager.printMessage(Diagnostic.Kind.NOTE, 
                "Generated zero-reflection mapper: " + qualifiedClassName);
                
        } catch (CompilePipeline.CompileException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "Failed to compile mapper implementation: " + e.getMessage(),
                e.element() != null ? e.element() : mapperInterface);
        }
    }
}

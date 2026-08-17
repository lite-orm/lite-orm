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
 * Annotation processor that generates ordinary Java Mapper implementations.
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
                
                generateZeroReflectionMapperImpl(typeElement);
            }
        }
    }
    
    /**
     * Generates one Mapper implementation.
     */
    private void generateZeroReflectionMapperImpl(TypeElement mapperInterface) throws IOException {
        try {
            if (!compilePipeline.supports(mapperInterface)) {
                messager.printMessage(Diagnostic.Kind.WARNING,
                    "Skipping unsupported interface: " + mapperInterface.getQualifiedName());
                return;
            }
            
            String packageName = elementUtils.getPackageOf(mapperInterface).getQualifiedName().toString();
            String className = mapperInterface.getSimpleName() + "Impl";
            String qualifiedClassName = packageName + "." + className;
            
            String javaCode = compilePipeline.compileMapper(mapperInterface);

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

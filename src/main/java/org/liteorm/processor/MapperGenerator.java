package org.liteorm.processor;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Mapper接口实现类生成器
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class MapperGenerator {
    
    private final Filer filer;
    private final Messager messager;
    private final Elements elementUtils;
    private final Types typeUtils;
    
    public MapperGenerator(Filer filer, Messager messager, Elements elementUtils, Types typeUtils) {
        this.filer = filer;
        this.messager = messager;
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
    }
    
    public void generateMapperImpl(TypeElement mapperInterface) throws IOException {
        String packageName = elementUtils.getPackageOf(mapperInterface).getQualifiedName().toString();
        String className = mapperInterface.getSimpleName() + "Impl";
        
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(TypeName.get(mapperInterface.asType()));
        
        // 为接口中的每个方法生成实现
        for (Element enclosedElement : mapperInterface.getEnclosedElements()) {
            if (enclosedElement instanceof ExecutableElement) {
                ExecutableElement method = (ExecutableElement) enclosedElement;
                generateMethodImplementation(classBuilder, method);
            }
        }
        
        TypeSpec typeSpec = classBuilder.build();
        JavaFile javaFile = JavaFile.builder(packageName, typeSpec).build();
        javaFile.writeTo(filer);
        
        messager.printMessage(Diagnostic.Kind.NOTE, 
            "Generated mapper implementation: " + packageName + "." + className);
    }
    
    private void generateMethodImplementation(TypeSpec.Builder classBuilder, ExecutableElement method) {
        String methodName = method.getSimpleName().toString();
        TypeName returnType = TypeName.get(method.getReturnType());
        
        // 构建方法参数
        List<ParameterSpec> parameters = new ArrayList<>();
        for (VariableElement param : method.getParameters()) {
            parameters.add(ParameterSpec.builder(
                TypeName.get(param.asType()), 
                param.getSimpleName().toString()
            ).build());
        }
        
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(Override.class)
            .returns(returnType)
            .addParameters(parameters);
        
        // 根据注解类型生成不同的实现
        String sql = extractSqlFromAnnotation(method);
        if (sql != null) {
            generateSqlMethodBody(methodBuilder, sql, method);
        } else {
            // 如果没有SQL注解，生成默认实现
            generateDefaultMethodBody(methodBuilder, returnType);
        }
        
        classBuilder.addMethod(methodBuilder.build());
    }
    
    private String extractSqlFromAnnotation(ExecutableElement method) {
        Select selectAnnotation = method.getAnnotation(Select.class);
        if (selectAnnotation != null && selectAnnotation.value().length > 0) {
            return selectAnnotation.value()[0];
        }
        
        Insert insertAnnotation = method.getAnnotation(Insert.class);
        if (insertAnnotation != null && insertAnnotation.value().length > 0) {
            return insertAnnotation.value()[0];
        }
        
        Update updateAnnotation = method.getAnnotation(Update.class);
        if (updateAnnotation != null && updateAnnotation.value().length > 0) {
            return updateAnnotation.value()[0];
        }
        
        Delete deleteAnnotation = method.getAnnotation(Delete.class);
        if (deleteAnnotation != null && deleteAnnotation.value().length > 0) {
            return deleteAnnotation.value()[0];
        }
        
        return null;
    }
    
    private void generateSqlMethodBody(MethodSpec.Builder methodBuilder, String sql, ExecutableElement method) {
        // 生成SQL执行逻辑的硬编码实现
        CodeBlock.Builder codeBuilder = CodeBlock.builder();
        
        // 添加SQL语句构建逻辑
        codeBuilder.addStatement("$T sql = $S", String.class, sql);
        
        // TODO: 这里将来会生成完整的责任链调用逻辑
        // 现在先生成一个简单的占位实现
        codeBuilder.addStatement("// TODO: Execute SQL through handler chain");
        codeBuilder.addStatement("// SQL: " + sql);
        
        TypeName returnType = TypeName.get(method.getReturnType());
        if (returnType.toString().startsWith("java.util.List")) {
            codeBuilder.addStatement("return new $T<>()", ArrayList.class);
        } else if (!returnType.equals(TypeName.VOID)) {
            codeBuilder.addStatement("return null");
        }
        
        methodBuilder.addCode(codeBuilder.build());
    }
    
    private void generateDefaultMethodBody(MethodSpec.Builder methodBuilder, TypeName returnType) {
        if (returnType.toString().startsWith("java.util.List")) {
            methodBuilder.addStatement("return new $T<>()", ArrayList.class);
        } else if (!returnType.equals(TypeName.VOID)) {
            methodBuilder.addStatement("return null");
        }
    }
}

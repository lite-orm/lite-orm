package org.liteorm.compile;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * 类型推断引擎 - 基于物理必需性的类型结构分析
 * 
 * 物理原理：
 * 1. 分析用户定义的返回类型结构（物理必需：了解目标对象）
 * 2. 推断构造器参数和顺序（物理必需：对象创建方式）
 * 3. 生成硬编码的对象构造代码（物理必需：零反射实现）
 * 4. 支持record class、普通class、基础类型（物理必需：通用性）
 * 
 * 设计原则：
 * - 编译期完全确定类型结构
 * - 生成类型安全的硬编码构造
 * - 支持嵌套类型和泛型
 * - 提供详细的编译期错误信息
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class TypeInferenceEngine {
    
    private final Elements elementUtils;
    private final Types typeUtils;
    
    public TypeInferenceEngine(Elements elementUtils, Types typeUtils) {
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
    }
    
    /**
     * 分析类型结构，生成类型信息
     * 
     * @param typeMirror 要分析的类型
     * @return 类型信息，包含构造方式和字段信息
     */
    public TypeInfo analyzeType(TypeMirror typeMirror) {
        String typeName = typeMirror.toString();
        
        // 处理基础类型
        if (isPrimitiveOrWrapper(typeName)) {
            return new TypeInfo(typeName, TypeInfo.ConstructorType.DIRECT_CAST, List.of());
        }
        
        // 处理String类型
        if ("java.lang.String".equals(typeName) || "String".equals(typeName)) {
            return new TypeInfo(typeName, TypeInfo.ConstructorType.DIRECT_CAST, List.of());
        }
        
        // 处理用户定义的类型
        if (typeMirror instanceof DeclaredType declaredType) {
            Element element = declaredType.asElement();
            if (element instanceof TypeElement typeElement) {
                return analyzeUserDefinedType(typeElement);
            }
        }
        
        // 未知类型，返回默认信息
        return new TypeInfo(typeName, TypeInfo.ConstructorType.UNSUPPORTED, List.of());
    }
    
    /**
     * 分析用户定义的类型（record class、普通class等）
     */
    private TypeInfo analyzeUserDefinedType(TypeElement typeElement) {
        String typeName = typeElement.getQualifiedName().toString();
        
        // 检查是否为record class
        if (isRecordClass(typeElement)) {
            return analyzeRecordClass(typeElement);
        }
        
        // 检查是否有合适的构造器
        List<FieldInfo> constructorFields = analyzeConstructors(typeElement);
        if (!constructorFields.isEmpty()) {
            return new TypeInfo(typeName, TypeInfo.ConstructorType.CONSTRUCTOR, constructorFields);
        }
        
        // 检查是否可以通过setter方式构造
        List<FieldInfo> setterFields = analyzeSetters(typeElement);
        if (!setterFields.isEmpty()) {
            return new TypeInfo(typeName, TypeInfo.ConstructorType.SETTER, setterFields);
        }
        
        // 无法分析的类型
        return new TypeInfo(typeName, TypeInfo.ConstructorType.UNSUPPORTED, List.of());
    }
    
    /**
     * 分析record class
     */
    private TypeInfo analyzeRecordClass(TypeElement recordElement) {
        List<FieldInfo> fields = new ArrayList<>();
        
        // record class的组件就是构造器参数
        for (Element enclosedElement : recordElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.RECORD_COMPONENT) {
                RecordComponentElement component = (RecordComponentElement) enclosedElement;
                String fieldName = component.getSimpleName().toString();
                String fieldType = component.asType().toString();
                
                fields.add(new FieldInfo(fieldName, fieldType, fields.size()));
            }
        }
        
        return new TypeInfo(
            recordElement.getQualifiedName().toString(),
            TypeInfo.ConstructorType.RECORD,
            fields
        );
    }
    
    /**
     * 分析构造器
     */
    private List<FieldInfo> analyzeConstructors(TypeElement typeElement) {
        // 寻找公共构造器
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.CONSTRUCTOR) {
                ExecutableElement constructor = (ExecutableElement) enclosedElement;
                
                // 检查是否为公共构造器
                if (constructor.getModifiers().contains(Modifier.PUBLIC)) {
                    List<FieldInfo> fields = new ArrayList<>();
                    List<? extends VariableElement> parameters = constructor.getParameters();
                    
                    for (int i = 0; i < parameters.size(); i++) {
                        VariableElement param = parameters.get(i);
                        String paramName = param.getSimpleName().toString();
                        String paramType = param.asType().toString();
                        
                        fields.add(new FieldInfo(paramName, paramType, i));
                    }
                    
                    return fields; // 返回第一个找到的公共构造器
                }
            }
        }
        
        return List.of();
    }
    
    /**
     * 分析setter方法
     */
    private List<FieldInfo> analyzeSetters(TypeElement typeElement) {
        List<FieldInfo> fields = new ArrayList<>();
        int index = 0;
        
        for (Element enclosedElement : typeElement.getEnclosedElements()) {
            if (enclosedElement.getKind() == ElementKind.METHOD) {
                ExecutableElement method = (ExecutableElement) enclosedElement;
                String methodName = method.getSimpleName().toString();
                
                // 检查是否为setter方法
                if (methodName.startsWith("set") && 
                    method.getModifiers().contains(Modifier.PUBLIC) &&
                    method.getParameters().size() == 1) {
                    
                    String fieldName = methodName.substring(3);
                    fieldName = Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
                    String fieldType = method.getParameters().get(0).asType().toString();
                    
                    fields.add(new FieldInfo(fieldName, fieldType, index++));
                }
            }
        }
        
        return fields;
    }
    
    /**
     * 检查是否为record class
     */
    private boolean isRecordClass(TypeElement typeElement) {
        // Java 14+的record class检查
        return typeElement.getKind() == ElementKind.RECORD;
    }
    
    /**
     * 检查是否为基础类型或包装类型
     */
    private boolean isPrimitiveOrWrapper(String typeName) {
        return switch (typeName) {
            case "byte", "short", "int", "long", "float", "double", "boolean", "char",
                 "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long",
                 "java.lang.Float", "java.lang.Double", "java.lang.Boolean", "java.lang.Character",
                 "Byte", "Short", "Integer", "Long", "Float", "Double", "Boolean", "Character" -> true;
            default -> false;
        };
    }
    
    /**
     * 生成对象构造代码
     */
    public String generateConstructorCode(TypeInfo typeInfo, String rowVariable) {
        return switch (typeInfo.getConstructorType()) {
            case DIRECT_CAST -> generateDirectCast(typeInfo.getTypeName(), rowVariable);
            case RECORD -> generateRecordConstructor(typeInfo, rowVariable);
            case CONSTRUCTOR -> generateClassConstructor(typeInfo, rowVariable);
            case SETTER -> generateSetterConstruction(typeInfo, rowVariable);
            case UNSUPPORTED -> "/* TODO: 不支持的类型 " + typeInfo.getTypeName() + " */ null";
        };
    }
    
    /**
     * 生成直接类型转换
     */
    private String generateDirectCast(String typeName, String rowVariable) {
        return "(" + typeName + ")" + rowVariable + "[0]";
    }
    
    /**
     * 生成record class构造
     */
    private String generateRecordConstructor(TypeInfo typeInfo, String rowVariable) {
        StringBuilder code = new StringBuilder();
        code.append("new ").append(typeInfo.getTypeName()).append("(");
        
        List<FieldInfo> fields = typeInfo.getFields();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) code.append(", ");
            FieldInfo field = fields.get(i);
            code.append("(").append(field.getFieldType()).append(")").append(rowVariable).append("[").append(i).append("]");
        }
        
        code.append(")");
        return code.toString();
    }
    
    /**
     * 生成普通类构造器调用
     */
    private String generateClassConstructor(TypeInfo typeInfo, String rowVariable) {
        // 与record class类似，但可能需要处理不同的参数顺序
        return generateRecordConstructor(typeInfo, rowVariable);
    }
    
    /**
     * 生成setter方式构造
     */
    private String generateSetterConstruction(TypeInfo typeInfo, String rowVariable) {
        StringBuilder code = new StringBuilder();
        String varName = "obj";
        
        code.append("({\n");
        code.append("            ").append(typeInfo.getTypeName()).append(" ").append(varName).append(" = new ").append(typeInfo.getTypeName()).append("();\n");
        
        for (FieldInfo field : typeInfo.getFields()) {
            String setterName = "set" + Character.toUpperCase(field.getFieldName().charAt(0)) + field.getFieldName().substring(1);
            code.append("            ").append(varName).append(".").append(setterName).append("((").append(field.getFieldType()).append(")").append(rowVariable).append("[").append(field.getIndex()).append("]);\n");
        }
        
        code.append("            ").append(varName).append(";\n");
        code.append("        })");
        
        return code.toString();
    }
    
    /**
     * 类型信息
     */
    public static class TypeInfo {
        private final String typeName;
        private final ConstructorType constructorType;
        private final List<FieldInfo> fields;
        
        public TypeInfo(String typeName, ConstructorType constructorType, List<FieldInfo> fields) {
            this.typeName = typeName;
            this.constructorType = constructorType;
            this.fields = fields;
        }
        
        // Getters
        public String getTypeName() { return typeName; }
        public ConstructorType getConstructorType() { return constructorType; }
        public List<FieldInfo> getFields() { return fields; }
        
        public enum ConstructorType {
            DIRECT_CAST,    // 直接类型转换：(String)row[0]
            RECORD,         // record class构造：new User(row[0], row[1])
            CONSTRUCTOR,    // 普通构造器：new User(row[0], row[1])
            SETTER,         // setter方式：obj.setName(row[0])
            UNSUPPORTED     // 不支持的类型
        }
    }
    
    /**
     * 字段信息
     */
    public static class FieldInfo {
        private final String fieldName;
        private final String fieldType;
        private final int index;
        
        public FieldInfo(String fieldName, String fieldType, int index) {
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.index = index;
        }
        
        // Getters
        public String getFieldName() { return fieldName; }
        public String getFieldType() { return fieldType; }
        public int getIndex() { return index; }
    }
}

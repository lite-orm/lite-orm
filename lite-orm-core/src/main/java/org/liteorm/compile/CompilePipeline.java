package org.liteorm.compile;

import org.liteorm.api.ExecutionPlan;

import javax.annotation.processing.Messager;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
final class CompilePipeline {

    private static final Pattern DOLLAR_SUBSTITUTION_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern METHOD_CALL_PATTERN = Pattern.compile("([a-zA-Z_][\\w.]*)\\s*\\(");
    
    private final List<SqlContentParser> sqlParsers;
    private final CodeGenerator codeGenerator;
    private final SqlParameterParser parameterParser;
    private final Elements elementUtils;
    private final Types typeUtils;
    private final Messager messager;
    
    public CompilePipeline(Elements elementUtils, Types typeUtils) {
        this(elementUtils, typeUtils, null, null);
    }

    public CompilePipeline(Elements elementUtils, Types typeUtils, Messager messager) {
        this(elementUtils, typeUtils, messager, null);
    }

    public CompilePipeline(Elements elementUtils, Types typeUtils, Messager messager, Filer filer) {
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
        this.messager = messager;
        
        // 初始化解析器（按优先级排序）
        this.sqlParsers = Arrays.asList(
            new XmlBasedSqlParser(filer),      // 优先级最高：XML覆盖注解
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
        } catch (CompileException e) {
            throw e;
        } catch (Exception e) {
            throw new CompileException("Failed to compile mapper: " + 
                mapperInterface.getQualifiedName() + ": " + e.getMessage(), e);
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
        if (mapperInterface.getAnnotation(org.liteorm.annotation.Mapper.class) != null) {
            return true;
        }
        
        // 检查是否有任何方法包含SQL注解
        return mapperInterface.getEnclosedElements().stream()
            .anyMatch(element -> element instanceof ExecutableElement &&
                (hasUseSqlProvider((ExecutableElement) element)
                    || sqlParsers.stream().anyMatch(parser -> parser.supports((ExecutableElement) element))));
    }
    
    /**
     * 分析接口方法
     */
    private List<MapperCompilationModel.MethodModel> analyzeInterfaceMethods(TypeElement mapperInterface)
            throws CompileException {
        Map<String, MapperCompilationModel.MethodModel> methodInfos = new LinkedHashMap<>();

        for (var element : elementUtils.getAllMembers(mapperInterface)) {
            if (element instanceof ExecutableElement method) {
                try {
                    ExecutableType resolvedMethodType = (ExecutableType) typeUtils.asMemberOf(
                        (DeclaredType) mapperInterface.asType(), method);
                    MapperCompilationModel.MethodModel methodInfo = analyzeMethod(
                        mapperInterface, method, resolvedMethodType);
                    if (methodInfo != null) {
                        methodInfos.putIfAbsent(methodKey(method, resolvedMethodType), methodInfo);
                    }
                } catch (CompileException exception) {
                    throw exception.element() == null ? exception.at(method) : exception;
                }
            }
        }
        
        return List.copyOf(methodInfos.values());
    }

    private String methodKey(ExecutableElement method, ExecutableType resolvedMethodType) {
        return method.getSimpleName() + resolvedMethodType.getParameterTypes().toString();
    }
    
    /**
     * 分析单个方法
     */
    private MapperCompilationModel.MethodModel analyzeMethod(
            TypeElement mapperInterface, ExecutableElement method, ExecutableType resolvedMethodType)
            throws CompileException {
        if (method.isDefault() && !hasExplicitSqlDeclaration(method)) {
            return null;
        }

        ProviderBinding providerBinding = analyzeProviderBinding(mapperInterface, method, resolvedMethodType);

        // 1. 解析SQL内容
        SqlContentParser.SqlParseResult sqlInfo = null;
        if (providerBinding == null) {
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
        }
        
        if (sqlInfo == null && providerBinding == null) {
            XmlBasedSqlParser xmlParser = (XmlBasedSqlParser) sqlParsers.get(0);
            if (xmlParser.hasMapperResource(method)) {
                throw new CompileException(
                    mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
                        + ": XML mapper exists but statement '" + method.getSimpleName() + "' was not found"
                );
            }
            return null; // 跳过没有SQL的方法
        }
        validateMethodSignature(mapperInterface, method);
        validateResolvedMethodTypes(mapperInterface, method, resolvedMethodType);
        if (sqlInfo != null) {
            validateSafeSqlSubstitution(mapperInterface, method, sqlInfo);
            validateDynamicExpressions(mapperInterface, method, sqlInfo.astNode());
            warnWhenXmlOverridesAnnotation(mapperInterface, method, sqlInfo);
        }

        ExecutionPlan.StatementType statementType = providerBinding == null
            ? mapStatementType(sqlInfo.sqlType())
            : providerBinding.statementType();
        CursorMethod cursorMethod = analyzeCursorMethod(
            mapperInterface, method, resolvedMethodType, statementType);

        // 2. 生成标准化参数模型和绑定顺序
        List<VariableElement> executionParameters = new ArrayList<>();
        List<TypeMirror> executionParameterTypes = new ArrayList<>();
        for (int index = 0; index < method.getParameters().size(); index++) {
            if (cursorMethod != null && cursorMethod.parameterIndex() == index) {
                continue;
            }
            executionParameters.add(method.getParameters().get(index));
            executionParameterTypes.add(resolvedMethodType.getParameterTypes().get(index));
        }
        List<SqlParameterParser.MethodParameter> methodParameters =
            parameterParser.describeMethodParameters(executionParameters, executionParameterTypes);
        SqlParameterParser.SqlParseResult parameterResult = new SqlParameterParser.SqlParseResult(
            sqlInfo == null ? "" : sqlInfo.sqlTemplate(), List.of());
        if (sqlInfo != null && !sqlInfo.isDynamic()) {
            try {
                parameterResult = sqlInfo.sqlType() == SqlContentParser.SqlType.BATCH
                    ? parseBatchParameters(mapperInterface, method, sqlInfo.sqlTemplate(), methodParameters)
                    : parameterParser.parseSql(sqlInfo.sqlTemplate(), methodParameters);
            } catch (Exception e) {
                throw new CompileException("Failed to resolve SQL parameters for " +
                    mapperInterface.getQualifiedName() + "#" + method.getSimpleName() + ": " + e.getMessage(), e);
            }
        }

        AdapterBindings adapterBindings = analyzeAdapterBindings(
            mapperInterface, method, resolvedMethodType, cursorMethod, sqlInfo, providerBinding,
            methodParameters, parameterResult.bindings());

        // 3. 构建方法信息
        String methodName = method.getSimpleName().toString();
        String returnType = resolvedMethodType.getReturnType().toString();
        String parameterList = buildParameterList(method, resolvedMethodType);
        String executionPlanParameterList = buildParameterList(
            method, resolvedMethodType, cursorMethod == null ? -1 : cursorMethod.parameterIndex());
        org.liteorm.annotation.GeneratedKey generatedKeyAnnotation =
            method.getAnnotation(org.liteorm.annotation.GeneratedKey.class);
        boolean generatedKey = generatedKeyAnnotation != null;
        String generatedKeyColumn = generatedKey ? generatedKeyAnnotation.value().trim() : null;
        validateWriteReturnType(mapperInterface, method, statementType, returnType, generatedKey);
        if (generatedKey) {
            validateGeneratedKeyMethod(
                mapperInterface, method, returnType, statementType, sqlInfo, providerBinding,
                adapterBindings.rowMapperFieldName() != null, generatedKeyColumn);
        }
        if (sqlInfo != null && sqlInfo.sqlType() == SqlContentParser.SqlType.BATCH) {
            validateBatchMethod(mapperInterface, method, returnType, methodParameters, sqlInfo);
        }
        ResultMapping resultMapping = sqlInfo != null && sqlInfo.sqlType() == SqlContentParser.SqlType.BATCH
            ? new ResultMapping("", "", List.of())
            : cursorMethod != null
                ? new ResultMapping("", "", List.of())
            : adapterBindings.rowMapperFieldName() == null
                ? generateResultMapping(mapperInterface, method, returnType)
                : new ResultMapping("(" + extractMappedType(returnType) + ")row[0]", "", List.of());

        return new MapperCompilationModel.MethodModel(
            methodName,
            returnType,
            parameterList,
            "build" + Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1) + "ExecutionPlan",
            mapperInterface.getQualifiedName() + "." + methodName,
            statementType,
            providerBinding == null ? mapSourceType(sqlInfo.sourceType()) : ExecutionPlan.SqlSource.GENERATED,
            providerBinding == null ? (sqlInfo.isDynamic() ? sqlInfo.sqlTemplate() : parameterResult.processedSql()) : "",
            providerBinding == null && sqlInfo.isDynamic(),
            generatedKeyColumn,
            returnType,
            resultMapping.expression(),
            resultMapping.helperCode(),
            resultMapping.columnLabels(),
            providerBinding == null ? null : providerBinding.providerClassName(),
            providerBinding == null ? null : methodName + "SqlProvider",
            providerBinding == null ? null : providerBinding.argumentExpression(),
            adapterBindings.adapterFields(),
            adapterBindings.parameterBinderFields(),
            adapterBindings.rowMapperFieldName(),
            methodParameters,
            parameterResult.bindings(),
            sqlInfo == null ? null : sqlInfo.astNode(),
            executionPlanParameterList,
            cursorMethod == null ? null : cursorMethod.parameterName()
        );
    }

    private AdapterBindings analyzeAdapterBindings(
            TypeElement mapperInterface, ExecutableElement method, ExecutableType resolvedMethodType,
            CursorMethod cursorMethod,
            SqlContentParser.SqlParseResult sqlInfo, ProviderBinding providerBinding,
            List<SqlParameterParser.MethodParameter> methodParameters,
            List<SqlParameterParser.ParameterBinding> parameterBindings) throws CompileException {
        List<MapperCompilationModel.AdapterField> fields = new ArrayList<>();
        List<String> binderFields = new ArrayList<>();
        String methodLocation = mapperInterface.getQualifiedName() + "#" + method.getSimpleName();

        boolean hasBinder = method.getParameters().stream()
            .anyMatch(parameter -> findAnnotation(parameter, "org.liteorm.annotation.UseParameterBinder") != null);
        if (hasBinder && providerBinding != null) {
            throw new CompileException(methodLocation
                + ": provider parameter binders require the typed BoundParameter contract");
        }

        java.util.Map<VariableElement, MapperCompilationModel.AdapterField> parameterAdapters =
            new java.util.LinkedHashMap<>();
        for (int parameterIndex = 0; parameterIndex < method.getParameters().size(); parameterIndex++) {
            if (cursorMethod != null && cursorMethod.parameterIndex() == parameterIndex) {
                continue;
            }
            VariableElement parameter = method.getParameters().get(parameterIndex);
            AnnotationMirror annotation = findAnnotation(
                parameter, "org.liteorm.annotation.UseParameterBinder");
            if (annotation == null) {
                continue;
            }
            TypeElement binderElement = validateAdapter(
                mapperInterface, method, annotationTypeValue(annotation, "value"),
                "org.liteorm.api.ParameterBinder", resolvedMethodType.getParameterTypes().get(parameterIndex),
                "parameter binder target type");
            String fieldName = method.getSimpleName() + capitalize(parameter.getSimpleName().toString())
                + "ParameterBinder";
            MapperCompilationModel.AdapterField adapterField = new MapperCompilationModel.AdapterField(
                binderElement.getQualifiedName().toString(), fieldName);
            parameterAdapters.put(parameter, adapterField);
            addAdapterField(fields, binderElement, fieldName);
        }

        if (sqlInfo != null && sqlInfo.isDynamic()) {
            for (int parameterIndex = 0; parameterIndex < method.getParameters().size(); parameterIndex++) {
                if (cursorMethod != null && cursorMethod.parameterIndex() == parameterIndex) {
                    continue;
                }
                VariableElement parameter = method.getParameters().get(parameterIndex);
                MapperCompilationModel.AdapterField adapterField = parameterAdapters.get(parameter);
                binderFields.add(adapterField == null ? null : adapterField.fieldName());
            }
        } else {
            for (SqlParameterParser.ParameterBinding binding : parameterBindings) {
                String root = binding.expression().split("\\.", 2)[0];
                VariableElement parameter = findMethodParameter(method, methodParameters, root);
                MapperCompilationModel.AdapterField adapterField = parameterAdapters.get(parameter);
                if (adapterField == null) {
                    binderFields.add(null);
                    continue;
                }
                if (binding.expression().contains(".")) {
                    throw new CompileException(methodLocation
                        + ": custom parameter binder must bind the whole Mapper parameter, not property "
                        + binding.expression());
                }
                binderFields.add(adapterField.fieldName());
            }
        }

        AnnotationMirror rowMapperAnnotation = findAnnotation(method, "org.liteorm.annotation.UseRowMapper");
        String rowMapperField = null;
        if (rowMapperAnnotation != null) {
            ExecutionPlan.StatementType statementType = providerBinding == null
                ? mapStatementType(sqlInfo.sqlType()) : providerBinding.statementType();
            boolean generatedKeyInsert = method.getAnnotation(org.liteorm.annotation.GeneratedKey.class) != null
                && statementType == ExecutionPlan.StatementType.INSERT;
            if (statementType != ExecutionPlan.StatementType.SELECT && !generatedKeyInsert) {
                throw new CompileException(methodLocation + ": row mapper requires a SELECT method");
            }
            String mappedType = cursorMethod == null
                ? extractMappedType(resolvedMethodType.getReturnType().toString())
                : cursorMethod.rowType().toString();
            TypeElement mappedTypeElement = elementUtils.getTypeElement(mappedType);
            TypeMirror mappedTypeMirror = mappedTypeElement == null
                ? cursorMethod == null ? resolvedMethodType.getReturnType() : cursorMethod.rowType()
                : mappedTypeElement.asType();
            TypeElement rowMapperElement = validateAdapter(
                mapperInterface, method, annotationTypeValue(rowMapperAnnotation, "value"),
                "org.liteorm.api.RowMapper", mappedTypeMirror, "row mapper target type");
            rowMapperField = method.getSimpleName() + "RowMapper";
            addAdapterField(fields, rowMapperElement, rowMapperField);
        }
        if (cursorMethod != null && rowMapperField == null) {
            throw new CompileException(methodLocation + ": cursor methods require @UseRowMapper");
        }
        return new AdapterBindings(
            List.copyOf(fields),
            java.util.Collections.unmodifiableList(new ArrayList<>(binderFields)),
            rowMapperField
        );
    }

    private VariableElement findMethodParameter(
            ExecutableElement method, List<SqlParameterParser.MethodParameter> descriptions, String alias) {
        for (SqlParameterParser.MethodParameter description : descriptions) {
            if (description.aliases().contains(alias)) {
                return method.getParameters().stream()
                    .filter(parameter -> parameter.getSimpleName().contentEquals(description.declaredName()))
                    .findFirst()
                    .orElse(null);
            }
        }
        return null;
    }

    private TypeElement validateAdapter(
            TypeElement mapperInterface, ExecutableElement method, TypeMirror adapterType,
            String interfaceName, TypeMirror expectedTarget, String mismatchLabel) throws CompileException {
        TypeElement adapterElement = (TypeElement) typeUtils.asElement(adapterType);
        String location = mapperInterface.getQualifiedName() + "#" + method.getSimpleName();
        if (adapterElement == null) {
            throw new CompileException(location + ": adapter type could not be resolved");
        }
        String mapperPackage = elementUtils.getPackageOf(mapperInterface).getQualifiedName().toString();
        boolean samePackage = mapperPackage.equals(
            elementUtils.getPackageOf(adapterElement).getQualifiedName().toString());
        if (adapterElement.getModifiers().contains(Modifier.ABSTRACT)
                || adapterElement.getModifiers().contains(Modifier.PRIVATE)
                || (!samePackage && !adapterElement.getModifiers().contains(Modifier.PUBLIC))) {
            throw new CompileException(location + ": adapter must be a concrete accessible class");
        }
        boolean hasNoArgConstructor = adapterElement.getEnclosedElements().stream()
            .filter(element -> element.getKind() == javax.lang.model.element.ElementKind.CONSTRUCTOR)
            .map(ExecutableElement.class::cast)
            .anyMatch(constructor -> constructor.getParameters().isEmpty()
                && (constructor.getModifiers().contains(Modifier.PUBLIC)
                    || (samePackage && !constructor.getModifiers().contains(Modifier.PRIVATE))));
        if (!hasNoArgConstructor) {
            throw new CompileException(location + ": adapter requires an accessible no-arg constructor");
        }
        TypeMirror actualTarget = findGenericInterfaceInput(adapterElement, interfaceName);
        if (actualTarget == null) {
            throw new CompileException(location + ": adapter must implement " + interfaceName + "<T>");
        }
        if (!typeUtils.isSameType(typeUtils.erasure(actualTarget), typeUtils.erasure(expectedTarget))) {
            throw new CompileException(location + ": " + mismatchLabel + " " + actualTarget
                + " does not match " + expectedTarget);
        }
        return adapterElement;
    }

    private TypeMirror findGenericInterfaceInput(TypeElement typeElement, String interfaceName) {
        for (TypeMirror interfaceType : typeElement.getInterfaces()) {
            if (interfaceType instanceof DeclaredType declaredType
                    && declaredType.asElement() instanceof TypeElement interfaceElement) {
                if (interfaceElement.getQualifiedName().contentEquals(interfaceName)
                        && declaredType.getTypeArguments().size() == 1) {
                    return declaredType.getTypeArguments().get(0);
                }
                TypeMirror inherited = findGenericInterfaceInput(interfaceElement, interfaceName);
                if (inherited != null) {
                    return inherited;
                }
            }
        }
        return null;
    }

    private void addAdapterField(
            List<MapperCompilationModel.AdapterField> fields, TypeElement adapterElement, String fieldName) {
        if (fields.stream().noneMatch(field -> field.fieldName().equals(fieldName))) {
            fields.add(new MapperCompilationModel.AdapterField(
                adapterElement.getQualifiedName().toString(), fieldName));
        }
    }

    private AnnotationMirror findAnnotation(javax.lang.model.element.Element element, String annotationName) {
        return element.getAnnotationMirrors().stream()
            .filter(annotation -> annotation.getAnnotationType().toString().equals(annotationName))
            .findFirst().orElse(null);
    }

    private String extractMappedType(String returnType) {
        if (returnType.startsWith("java.util.List<")) {
            return extractListElementType(returnType);
        }
        if (returnType.startsWith("java.util.Optional<")) {
            return extractOptionalElementType(returnType);
        }
        return returnType;
    }

    private String capitalize(String value) {
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private record AdapterBindings(
        List<MapperCompilationModel.AdapterField> adapterFields,
        List<String> parameterBinderFields,
        String rowMapperFieldName) {
    }

    private ProviderBinding analyzeProviderBinding(
            TypeElement mapperInterface, ExecutableElement method, ExecutableType resolvedMethodType)
            throws CompileException {
        AnnotationMirror annotation = findUseSqlProvider(method);
        if (annotation == null) {
            return null;
        }
        AnnotationBasedSqlParser annotationParser = (AnnotationBasedSqlParser) sqlParsers.get(1);
        XmlBasedSqlParser xmlParser = (XmlBasedSqlParser) sqlParsers.get(0);
        if (annotationParser.supports(method) || xmlParser.parseSql(method) != null) {
            throw new CompileException(mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
                + ": SQL provider cannot be combined with XML or SQL annotations");
        }
        if (method.getParameters().size() > 1) {
            throw new CompileException(mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
                + ": SQL provider methods support zero or one Mapper parameter; wrap multiple values in a record");
        }

        TypeMirror providerType = annotationTypeValue(annotation, "value");
        String statementTypeName = annotationEnumValue(annotation, "statementType");
        TypeElement providerElement = (TypeElement) typeUtils.asElement(providerType);
        String mapperPackage = elementUtils.getPackageOf(mapperInterface).getQualifiedName().toString();
        String providerPackage = elementUtils.getPackageOf(providerElement).getQualifiedName().toString();
        boolean samePackage = mapperPackage.equals(providerPackage);
        if (providerElement == null || providerElement.getModifiers().contains(Modifier.PRIVATE)
                || providerElement.getModifiers().contains(Modifier.ABSTRACT)
                || (!samePackage && !providerElement.getModifiers().contains(Modifier.PUBLIC))) {
            throw new CompileException(mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
                + ": SQL provider must be a concrete accessible class");
        }
        boolean hasAccessibleNoArg = providerElement.getEnclosedElements().stream()
            .filter(element -> element.getKind() == javax.lang.model.element.ElementKind.CONSTRUCTOR)
            .map(ExecutableElement.class::cast)
            .anyMatch(constructor -> constructor.getParameters().isEmpty()
                && (constructor.getModifiers().contains(Modifier.PUBLIC)
                    || (samePackage && !constructor.getModifiers().contains(Modifier.PRIVATE))));
        if (!hasAccessibleNoArg) {
            throw new CompileException(mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
                + ": SQL provider requires an accessible no-arg constructor");
        }

        TypeMirror providerInput = findSqlProviderInput(providerElement);
        if (providerInput == null) {
            throw new CompileException(mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
                + ": provider must implement org.liteorm.api.SqlProvider<P>");
        }
        TypeMirror mapperInput = method.getParameters().isEmpty()
            ? elementUtils.getTypeElement("java.lang.Void").asType()
            : resolvedMethodType.getParameterTypes().get(0);
        if (!typeUtils.isSameType(typeUtils.erasure(providerInput), typeUtils.erasure(mapperInput))) {
            throw new CompileException(mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
                + ": provider input type " + providerInput + " does not match Mapper parameter type " + mapperInput);
        }
        return new ProviderBinding(
            providerElement.getQualifiedName().toString(),
            method.getParameters().isEmpty() ? "null" : method.getParameters().get(0).getSimpleName().toString(),
            ExecutionPlan.StatementType.valueOf(statementTypeName)
        );
    }

    private TypeMirror findSqlProviderInput(TypeElement providerElement) {
        for (TypeMirror interfaceType : providerElement.getInterfaces()) {
            if (interfaceType instanceof DeclaredType declaredType
                    && declaredType.asElement() instanceof TypeElement interfaceElement
                    && interfaceElement.getQualifiedName().contentEquals("org.liteorm.api.SqlProvider")
                    && declaredType.getTypeArguments().size() == 1) {
                return declaredType.getTypeArguments().get(0);
            }
        }
        TypeMirror superclass = providerElement.getSuperclass();
        if (superclass instanceof DeclaredType declaredSuperclass
                && declaredSuperclass.asElement() instanceof TypeElement superclassElement
                && !superclassElement.getQualifiedName().contentEquals("java.lang.Object")) {
            return findSqlProviderInput(superclassElement);
        }
        return null;
    }

    private boolean hasUseSqlProvider(ExecutableElement method) {
        return findUseSqlProvider(method) != null;
    }

    private AnnotationMirror findUseSqlProvider(ExecutableElement method) {
        return method.getAnnotationMirrors().stream()
            .filter(annotation -> annotation.getAnnotationType().toString()
                .equals("org.liteorm.annotation.UseSqlProvider"))
            .findFirst()
            .orElse(null);
    }

    private TypeMirror annotationTypeValue(AnnotationMirror annotation, String name) {
        return (TypeMirror) annotationValue(annotation, name).getValue();
    }

    private String annotationEnumValue(AnnotationMirror annotation, String name) {
        VariableElement value = (VariableElement) annotationValue(annotation, name).getValue();
        return value.getSimpleName().toString();
    }

    private AnnotationValue annotationValue(AnnotationMirror annotation, String name) {
        return elementUtils.getElementValuesWithDefaults(annotation).entrySet().stream()
            .filter(entry -> entry.getKey().getSimpleName().contentEquals(name))
            .map(java.util.Map.Entry::getValue)
            .findFirst()
            .orElseThrow();
    }

    private record ProviderBinding(
        String providerClassName, String argumentExpression, ExecutionPlan.StatementType statementType) {
    }

    private void validateSafeSqlSubstitution(
            TypeElement mapperInterface,
            ExecutableElement method,
            SqlContentParser.SqlParseResult sqlInfo) throws CompileException {
        String expression = findDollarSubstitution(sqlInfo.sqlTemplate());
        if (expression == null && sqlInfo.astNode() != null) {
            expression = findDollarSubstitution(sqlInfo.astNode());
        }
        if (expression != null) {
            throw new CompileException(
                mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
                    + ": Unsafe SQL substitution ${" + expression + "} is not supported; "
                    + "use #{...} for values or a compile-time-bound SQL provider for dynamic SQL structure"
            );
        }
    }

    private String findDollarSubstitution(AstNode astNode) {
        if (astNode instanceof AstNode.TextNode textNode) {
            return findDollarSubstitution(textNode.text());
        }
        for (AstNode child : astNode.getChildren()) {
            String expression = findDollarSubstitution(child);
            if (expression != null) {
                return expression;
            }
        }
        return null;
    }

    private String findDollarSubstitution(String sql) {
        Matcher matcher = DOLLAR_SUBSTITUTION_PATTERN.matcher(sql == null ? "" : sql);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private void validateDynamicExpressions(
            TypeElement mapperInterface,
            ExecutableElement method,
            AstNode astNode) throws CompileException {
        if (astNode == null) {
            return;
        }

        String unsupportedExpression = findUnsupportedExpression(astNode);
        if (unsupportedExpression != null) {
            throw new CompileException(
                mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
                    + ": Unsupported dynamic SQL expression: " + unsupportedExpression
            );
        }
    }

    private String findUnsupportedExpression(AstNode astNode) {
        String expression = switch (astNode) {
            case AstNode.IfNode ifNode -> ifNode.test();
            case AstNode.WhenNode whenNode -> whenNode.test();
            case AstNode.BindNode bindNode -> bindNode.value();
            default -> null;
        };
        if (expression != null && !isSupportedExpression(expression)) {
            return expression;
        }

        for (AstNode child : astNode.getChildren()) {
            String unsupportedExpression = findUnsupportedExpression(child);
            if (unsupportedExpression != null) {
                return unsupportedExpression;
            }
        }
        return null;
    }

    private boolean isSupportedExpression(String expression) {
        if (expression == null || expression.isBlank() || expression.indexOf('@') >= 0) {
            return false;
        }

        Matcher methodMatcher = METHOD_CALL_PATTERN.matcher(expression);
        while (methodMatcher.find()) {
            String methodPath = methodMatcher.group(1);
            if (!methodPath.endsWith(".size")) {
                return false;
            }
        }
        return true;
    }

    private void warnWhenXmlOverridesAnnotation(
            TypeElement mapperInterface,
            ExecutableElement method,
            SqlContentParser.SqlParseResult sqlInfo) {
        if (messager == null) {
            return;
        }

        SqlContentParser annotationParser = sqlParsers.get(1);
        if (sqlInfo.sourceType() == SqlContentParser.SqlSourceType.XML && annotationParser.supports(method)) {
            messager.printMessage(
                Diagnostic.Kind.WARNING,
                "XML SQL overrides annotation SQL for " + mapperInterface.getQualifiedName()
                    + "#" + method.getSimpleName(),
                method
            );
        }
    }

    private void validateMethodSignature(TypeElement mapperInterface, ExecutableElement method) throws CompileException {
        String methodLocation = mapperInterface.getQualifiedName() + "#" + method.getSimpleName();
        if (method.isVarArgs()) {
            throw new CompileException(methodLocation + ": varargs mapper methods are not supported");
        }
        if (method.getModifiers().contains(javax.lang.model.element.Modifier.STATIC)) {
            throw new CompileException(methodLocation + ": static mapper methods are not supported");
        }
    }

    private boolean hasExplicitSqlDeclaration(ExecutableElement method) {
        return hasUseSqlProvider(method) || sqlParsers.get(1).supports(method);
    }

    private void validateResolvedMethodTypes(
            TypeElement mapperInterface, ExecutableElement method, ExecutableType resolvedMethodType)
            throws CompileException {
        String unresolvedReturnType = findUnresolvedGenericType(resolvedMethodType.getReturnType());
        if (unresolvedReturnType != null) {
            throw new CompileException(mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
                + ": unresolved generic type " + unresolvedReturnType + " in return type "
                + resolvedMethodType.getReturnType());
        }
        for (TypeMirror parameterType : resolvedMethodType.getParameterTypes()) {
            String unresolvedParameterType = findUnresolvedGenericType(parameterType);
            if (unresolvedParameterType != null) {
                throw new CompileException(mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
                    + ": unresolved generic type " + unresolvedParameterType + " in parameter type "
                    + parameterType);
            }
        }
    }

    private CursorMethod analyzeCursorMethod(
            TypeElement mapperInterface,
            ExecutableElement method,
            ExecutableType resolvedMethodType,
            ExecutionPlan.StatementType statementType) throws CompileException {
        String location = mapperInterface.getQualifiedName() + "#" + method.getSimpleName();
        TypeMirror returnType = resolvedMethodType.getReturnType();
        if (isType(returnType, "org.liteorm.api.RowCursor")
                || isType(returnType, "java.util.stream.Stream")) {
            throw new CompileException(location + ": cursor rows cannot escape the callback scope");
        }

        CursorMethod cursorMethod = null;
        for (int index = 0; index < resolvedMethodType.getParameterTypes().size(); index++) {
            TypeMirror parameterType = resolvedMethodType.getParameterTypes().get(index);
            if (!isType(parameterType, "org.liteorm.api.CursorCallback")) {
                continue;
            }
            if (cursorMethod != null) {
                throw new CompileException(location + ": cursor methods require exactly one CursorCallback<T, R>");
            }
            if (!(parameterType instanceof DeclaredType declaredType)
                    || declaredType.getTypeArguments().size() != 2) {
                throw new CompileException(location + ": CursorCallback must declare row and result types");
            }
            cursorMethod = new CursorMethod(
                index,
                method.getParameters().get(index).getSimpleName().toString(),
                declaredType.getTypeArguments().get(0),
                declaredType.getTypeArguments().get(1)
            );
        }
        if (cursorMethod == null) {
            return null;
        }
        if (statementType != ExecutionPlan.StatementType.SELECT) {
            throw new CompileException(location + ": cursor methods require a SELECT statement");
        }
        TypeMirror comparableReturnType = returnType.getKind().isPrimitive()
            ? typeUtils.boxedClass((PrimitiveType) returnType).asType()
            : returnType;
        if (!typeUtils.isSameType(comparableReturnType, cursorMethod.resultType())) {
            throw new CompileException(location + ": cursor callback result type " + cursorMethod.resultType()
                + " does not match Mapper return type " + returnType);
        }
        return cursorMethod;
    }

    private boolean isType(TypeMirror type, String qualifiedName) {
        return type.getKind() != TypeKind.VOID
            && typeUtils.erasure(type).toString().equals(qualifiedName);
    }

    private String findUnresolvedGenericType(TypeMirror type) {
        if (type.getKind() == TypeKind.TYPEVAR || type.getKind() == TypeKind.WILDCARD) {
            return type.toString();
        }
        if (type.getKind() == TypeKind.ARRAY) {
            return findUnresolvedGenericType(((ArrayType) type).getComponentType());
        }
        if (type instanceof DeclaredType declaredType) {
            for (TypeMirror typeArgument : declaredType.getTypeArguments()) {
                String unresolvedType = findUnresolvedGenericType(typeArgument);
                if (unresolvedType != null) {
                    return unresolvedType;
                }
            }
        }
        return null;
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

    private SqlParameterParser.SqlParseResult parseBatchParameters(
            TypeElement mapperInterface,
            ExecutableElement method,
            String sql,
            List<SqlParameterParser.MethodParameter> methodParameters) throws CompileException {
        validateBatchMethod(
            mapperInterface, method, method.getReturnType().toString(), methodParameters, null);
        String elementType = batchElementType(methodParameters.get(0).typeName());
        SqlParameterParser.MethodParameter item = new SqlParameterParser.MethodParameter(
            "item", "item", elementType, List.of("item")
        );
        return parameterParser.parseSql(sql, List.of(item));
    }

    private void validateBatchMethod(
            TypeElement mapperInterface,
            ExecutableElement method,
            String returnType,
            List<SqlParameterParser.MethodParameter> methodParameters,
            SqlContentParser.SqlParseResult sqlInfo) throws CompileException {
        String location = mapperInterface.getQualifiedName() + "#" + method.getSimpleName();
        if (methodParameters.size() != 1
                || !methodParameters.get(0).typeName().startsWith("java.util.List<")) {
            throw new CompileException(location + ": batch methods require exactly one java.util.List<T> parameter");
        }
        if (!"int[]".equals(returnType)) {
            throw new CompileException(location + ": batch methods must return int[]");
        }
        if (sqlInfo != null && sqlInfo.isDynamic()) {
            throw new CompileException(location + ": dynamic SQL is not supported for JDBC batch methods");
        }
    }

    private void validateGeneratedKeyMethod(
            TypeElement mapperInterface,
            ExecutableElement method,
            String returnType,
            ExecutionPlan.StatementType statementType,
            SqlContentParser.SqlParseResult sqlInfo,
            ProviderBinding providerBinding,
            boolean hasRowMapper,
            String generatedKeyColumn) throws CompileException {
        String location = mapperInterface.getQualifiedName() + "#" + method.getSimpleName();
        if (generatedKeyColumn.isBlank()) {
            throw new CompileException(location + ": generated-key column must not be blank");
        }
        if (providerBinding != null) {
            throw new CompileException(location + ": generated keys are not supported with SQL providers");
        }
        if (statementType == ExecutionPlan.StatementType.BATCH) {
            throw new CompileException(location + ": generated keys are not supported for batch methods");
        }
        if (statementType != ExecutionPlan.StatementType.INSERT) {
            throw new CompileException(location + ": generated keys require an INSERT statement");
        }
        if (sqlInfo != null && sqlInfo.isDynamic()) {
            throw new CompileException(location + ": generated keys require static SQL");
        }
        if (!hasRowMapper && !isSupportedGeneratedKeyScalar(returnType)) {
            throw new CompileException(location + ": generated-key return type " + returnType
                + " requires @UseRowMapper");
        }
    }

    private boolean isSupportedGeneratedKeyScalar(String returnType) {
        return switch (returnType) {
            case "int", "java.lang.Integer", "long", "java.lang.Long",
                "short", "java.lang.Short", "byte", "java.lang.Byte",
                "double", "java.lang.Double", "float", "java.lang.Float",
                "java.math.BigDecimal", "java.lang.String" -> true;
            default -> false;
        };
    }

    private void validateSingleResultReturnType(
            TypeElement mapperInterface,
            ExecutableElement method,
            ExecutionPlan.StatementType statementType,
            String returnType) throws CompileException {
        if (statementType != ExecutionPlan.StatementType.SELECT
                || returnType.startsWith("java.util.List<")
                || !method.getReturnType().getKind().isPrimitive()) {
            return;
        }
        String location = mapperInterface.getQualifiedName() + "#" + method.getSimpleName();
        throw new CompileException(
            location + ": primitive SELECT return types cannot represent zero rows; use a wrapper type");
    }

    private void validateWriteReturnType(
            TypeElement mapperInterface,
            ExecutableElement method,
            ExecutionPlan.StatementType statementType,
            String returnType,
            boolean generatedKey) throws CompileException {
        if (statementType == ExecutionPlan.StatementType.SELECT
                || statementType == ExecutionPlan.StatementType.BATCH
                || generatedKey
                || "void".equals(returnType)
                || "int".equals(returnType)
                || "long".equals(returnType)) {
            return;
        }
        String location = mapperInterface.getQualifiedName() + "#" + method.getSimpleName();
        throw new CompileException(location + ": write methods must return void, int, or long");
    }

    private String batchElementType(String listType) {
        return listType.substring(listType.indexOf('<') + 1, listType.lastIndexOf('>'));
    }

    private ExecutionPlan.SqlSource mapSourceType(SqlContentParser.SqlSourceType sourceType) {
        return switch (sourceType) {
            case XML -> ExecutionPlan.SqlSource.XML;
            case ANNOTATION -> ExecutionPlan.SqlSource.ANNOTATION;
            case SCRIPT -> ExecutionPlan.SqlSource.SCRIPT;
        };
    }

    /**
     * 构建参数列表字符串
     */
    private String buildParameterList(ExecutableElement method, ExecutableType resolvedMethodType) {
        return buildParameterList(method, resolvedMethodType, -1);
    }

    private String buildParameterList(
            ExecutableElement method, ExecutableType resolvedMethodType, int excludedParameterIndex) {
        List<String> params = new ArrayList<>();
        for (int index = 0; index < method.getParameters().size(); index++) {
            if (index == excludedParameterIndex) {
                continue;
            }
            VariableElement param = method.getParameters().get(index);
            String paramType = resolvedMethodType.getParameterTypes().get(index).toString();
            String paramName = param.getSimpleName().toString();
            params.add(paramType + " " + paramName);
        }
        return String.join(", ", params);
    }
    
    /**
     * 生成结果映射代码
     */
    private ResultMapping generateResultMapping(
            TypeElement mapperInterface, ExecutableElement method, String returnType) throws CompileException {
        if (returnType.contains("List<")) {
            String elementType = extractListElementType(returnType);
            return generateSingleMapping(mapperInterface, method, elementType);
        } else if (returnType.startsWith("java.util.Optional<")) {
            return generateSingleMapping(mapperInterface, method, extractOptionalElementType(returnType));
        } else if (!returnType.equals("void")) {
            return generateSingleMapping(mapperInterface, method, returnType);
        } else {
            return new ResultMapping("", "", List.of());
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

    private String extractOptionalElementType(String optionalType) {
        int start = optionalType.indexOf('<') + 1;
        int end = optionalType.lastIndexOf('>');
        return optionalType.substring(start, end);
    }
    
    /**
     * 生成单对象映射代码
     */
    private ResultMapping generateSingleMapping(
            TypeElement mapperInterface, ExecutableElement method, String objectType) throws CompileException {
        String scalarMapping = generateValueMapping(objectType, "row[0]");
        if (scalarMapping != null) {
            return new ResultMapping(scalarMapping, "", List.of());
        }

        var typeElement = elementUtils.getTypeElement(objectType);
        if (typeElement == null) {
            throw unsupportedResultMapping(mapperInterface, method, objectType);
        }

        if (typeElement.getKind() == javax.lang.model.element.ElementKind.RECORD) {
            return generateRecordMapping(mapperInterface, method, typeElement, objectType);
        }

        return generateJavaBeanMapping(mapperInterface, method, typeElement, objectType);
    }

    private String generateScalarMapping(String objectType, String valueExpression) {
        return switch (objectType) {
            case "java.lang.String" -> "(java.lang.String)" + valueExpression;
            case "java.lang.Long", "long" -> "ResultValueConverters.toLong(" + valueExpression + ")";
            case "java.lang.Integer", "int" -> "ResultValueConverters.toInteger(" + valueExpression + ")";
            case "java.lang.Short", "short" -> "ResultValueConverters.toShort(" + valueExpression + ")";
            case "java.lang.Byte", "byte" -> "ResultValueConverters.toByte(" + valueExpression + ")";
            case "java.lang.Double", "double" -> "ResultValueConverters.toDouble(" + valueExpression + ")";
            case "java.lang.Float", "float" -> "ResultValueConverters.toFloat(" + valueExpression + ")";
            case "java.math.BigDecimal" -> "ResultValueConverters.toBigDecimal(" + valueExpression + ")";
            case "java.lang.Boolean", "boolean" -> "ResultValueConverters.toBoolean(" + valueExpression + ")";
            case "java.lang.Character", "char" -> "ResultValueConverters.toCharacter(" + valueExpression + ")";
            case "java.time.LocalDate" -> "ResultValueConverters.toLocalDate(" + valueExpression + ")";
            case "java.time.LocalDateTime" -> "ResultValueConverters.toLocalDateTime(" + valueExpression + ")";
            case "java.time.Instant" -> "ResultValueConverters.toInstant(" + valueExpression + ")";
            case "byte[]" -> "(byte[])" + valueExpression;
            default -> null;
        };
    }

    private String generateValueMapping(String objectType, String valueExpression) {
        String scalarMapping = generateScalarMapping(objectType, valueExpression);
        if (scalarMapping != null) {
            return scalarMapping;
        }
        TypeElement typeElement = elementUtils.getTypeElement(objectType);
        if (typeElement != null && typeElement.getKind() == javax.lang.model.element.ElementKind.ENUM) {
            return valueExpression + " == null ? null : " + objectType + ".valueOf("
                + valueExpression + ".toString())";
        }
        return null;
    }
    
    /**
     * 生成record class映射代码（零反射）
     */
    private ResultMapping generateRecordMapping(
            TypeElement mapperInterface, ExecutableElement mapperMethod,
            TypeElement typeElement, String objectType) throws CompileException {
        // 获取record components
        var recordComponents = typeElement.getRecordComponents();
        List<String> columnLabels = new ArrayList<>(recordComponents.size());
        
        StringBuilder mapping = new StringBuilder("new " + objectType + "(");
        for (int i = 0; i < recordComponents.size(); i++) {
            var component = recordComponents.get(i);
            String componentName = component.getSimpleName().toString();
            String componentType = component.asType().toString();
            columnLabels.add(resultColumnLabel(component, componentName));
            
            if (i > 0) {
                mapping.append(", ");
            }
            
            String convertedValue = generateValueMapping(
                componentType, "row[resultColumnIndexes[" + i + "]]");
            if (convertedValue == null) {
                throw unsupportedResultMapping(mapperInterface, mapperMethod,
                    "nested record component " + objectType + "." + componentName
                        + " (" + componentType + ")");
            }
            mapping.append(convertedValue);
        }
        mapping.append(")");
        
        return new ResultMapping(mapping.toString(), "", List.copyOf(columnLabels));
    }
    
    /**
     * 生成普通类映射代码
     */
    private ResultMapping generateJavaBeanMapping(
            TypeElement mapperInterface, ExecutableElement mapperMethod,
            TypeElement typeElement, String objectType) throws CompileException {
        boolean hasAccessibleNoArgConstructor = typeElement.getEnclosedElements().stream()
            .filter(element -> element.getKind() == javax.lang.model.element.ElementKind.CONSTRUCTOR)
            .map(ExecutableElement.class::cast)
            .anyMatch(constructor -> constructor.getParameters().isEmpty()
                && !constructor.getModifiers().contains(Modifier.PRIVATE));
        if (!hasAccessibleNoArgConstructor) {
            throw unsupportedResultMapping(mapperInterface, mapperMethod, objectType
                + " requires an accessible no-arg constructor");
        }

        List<VariableElement> fields = typeElement.getEnclosedElements().stream()
            .filter(element -> element.getKind() == javax.lang.model.element.ElementKind.FIELD)
            .map(VariableElement.class::cast)
            .filter(field -> !field.getModifiers().contains(Modifier.STATIC))
            .toList();
        if (fields.isEmpty()) {
            throw unsupportedResultMapping(mapperInterface, mapperMethod, objectType + " has no mappable fields");
        }

        String helperName = "map" + Character.toUpperCase(mapperMethod.getSimpleName().charAt(0))
            + mapperMethod.getSimpleName().toString().substring(1) + "Row";
        StringBuilder helper = new StringBuilder();
        helper.append("    private ").append(objectType).append(" ").append(helperName)
            .append("(Object[] row, int[] resultColumnIndexes) {\n");
        helper.append("        ").append(objectType).append(" mapped = new ").append(objectType).append("();\n");
        List<String> columnLabels = new ArrayList<>(fields.size());

        for (int index = 0; index < fields.size(); index++) {
            VariableElement field = fields.get(index);
            String fieldName = field.getSimpleName().toString();
            columnLabels.add(resultColumnLabel(field, fieldName));
            String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            ExecutableElement setter = typeElement.getEnclosedElements().stream()
                .filter(element -> element.getKind() == javax.lang.model.element.ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(candidate -> candidate.getSimpleName().contentEquals(setterName))
                .filter(candidate -> candidate.getModifiers().contains(Modifier.PUBLIC))
                .filter(candidate -> candidate.getParameters().size() == 1)
                .findFirst()
                .orElseThrow(() -> unsupportedResultMapping(mapperInterface, mapperMethod,
                    objectType + " is missing public setter " + setterName));
            String parameterType = setter.getParameters().get(0).asType().toString();
            String convertedValue = generateValueMapping(
                parameterType, "row[resultColumnIndexes[" + index + "]]");
            if (convertedValue == null) {
                throw unsupportedResultMapping(mapperInterface, mapperMethod,
                    "nested object property " + objectType + "." + fieldName + " (" + parameterType + ")");
            }
            helper.append("        mapped.").append(setterName).append("(")
                .append(convertedValue).append(");\n");
        }
        helper.append("        return mapped;\n");
        helper.append("    }\n");
        return new ResultMapping(
            helperName + "(row, resultColumnIndexes)", helper.toString(), List.copyOf(columnLabels));
    }

    private String resultColumnLabel(Element element, String defaultLabel) throws CompileException {
        AnnotationMirror annotation = findAnnotation(element, "org.liteorm.annotation.Column");
        if (annotation == null) {
            return defaultLabel;
        }
        String label = (String) annotationValue(annotation, "value").getValue();
        if (label.isBlank()) {
            throw new CompileException(element + ": @Column value must not be blank");
        }
        return label;
    }

    private CompileException unsupportedResultMapping(
            TypeElement mapperInterface, ExecutableElement method, String detail) {
        return new CompileException(mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
            + ": Unsupported result mapping: " + detail);
    }

    private record ResultMapping(String expression, String helperCode, List<String> columnLabels) {
    }

    private record CursorMethod(
        int parameterIndex, String parameterName, TypeMirror rowType, TypeMirror resultType) {
    }
    
    /**
     * 编译异常
     */
    public static class CompileException extends Exception {
        private final Element element;

        public CompileException(String message) {
            this(message, null, null);
        }
        
        public CompileException(String message, Throwable cause) {
            this(message, cause, null);
        }

        private CompileException(String message, Throwable cause, Element element) {
            super(message, cause);
            this.element = element;
        }

        public Element element() {
            return element;
        }

        public CompileException at(Element diagnosticElement) {
            return new CompileException(getMessage(), getCause(), diagnosticElement);
        }
    }
}

package org.liteorm.compile;

import org.liteorm.api.ExecutionPlan;

import javax.annotation.processing.Messager;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Coordinates SQL parsing, validation, compilation modeling, and source generation.
 * 
 * @author lite-orm
 * @since 2024/10/01
 */
final class CompilePipeline {

    private static final Pattern DOLLAR_SUBSTITUTION_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern HASH_PARAMETER_PATTERN = Pattern.compile("#\\{([^}]+)}");
    private static final Pattern METHOD_CALL_PATTERN = Pattern.compile("([a-zA-Z_][\\w.]*)\\s*\\(");
    
    private final List<SqlContentParser> sqlParsers;
    private final CodeGenerator codeGenerator;
    private final SqlParameterParser parameterParser;
    private final Elements elementUtils;
    private final Types typeUtils;
    private final Messager messager;
    private final JdbcTypeMappingsValidator jdbcTypeMappingsValidator;
    
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
        
        this.sqlParsers = Arrays.asList(
            new XmlBasedSqlParser(filer),
            new AnnotationBasedSqlParser()
        );

        this.codeGenerator = new FreemarkerCodeGenerator();
        this.parameterParser = new SqlParameterParser();
        this.jdbcTypeMappingsValidator = new JdbcTypeMappingsValidator(elementUtils, typeUtils, messager);
    }
    
    /**
     * Compiles a Mapper interface into Java source.
     * 
     * @param mapperInterface Mapper interface
     * @return generated Java source
     * @throws CompileException when validation or generation fails
     */
    public String compileMapper(TypeElement mapperInterface) throws CompileException {
        try {
            MapperCompilationModel compilationModel = buildCompilationModel(mapperInterface);

            if (compilationModel.methods().isEmpty()) {
                throw new CompileException("No SQL methods found in interface " + 
                    mapperInterface.getQualifiedName());
            }
            
            return codeGenerator.generateMapperImpl(mapperInterface, compilationModel, elementUtils, typeUtils);
        } catch (CompileException e) {
            throw e;
        } catch (Exception e) {
            throw new CompileException("Failed to compile mapper: " + 
                mapperInterface.getQualifiedName() + ": " + e.getMessage(), e);
        }
    }

    public MapperCompilationModel buildCompilationModel(TypeElement mapperInterface) throws CompileException {
        JdbcTypeMappingsSelection jdbcTypeMappings = requireJdbcTypeMappingsSelection(mapperInterface);
        List<MapperCompilationModel.MethodModel> methods = analyzeInterfaceMethods(
            mapperInterface, jdbcTypeMappings);
        String packageName = elementUtils.getPackageOf(mapperInterface).getQualifiedName().toString();
        String interfaceName = mapperInterface.getSimpleName().toString();
        return new MapperCompilationModel(
            packageName,
            interfaceName,
            interfaceName + "Impl",
            mapperInterface.getQualifiedName().toString(),
            jdbcTypeMappings.type().getQualifiedName().toString(),
            methods
        );
    }

    private JdbcTypeMappingsSelection requireJdbcTypeMappingsSelection(TypeElement mapperInterface)
            throws CompileException {
        var mapperPackage = elementUtils.getPackageOf(mapperInterface);
        List<? extends AnnotationMirror> selections = mapperPackage.getAnnotationMirrors().stream()
            .filter(annotation -> annotation.getAnnotationType().toString()
                .equals("org.liteorm.annotation.UseJdbcTypeMappings"))
            .toList();
        if (selections.size() != 1) {
            throw new CompileException(
                "Mapper package " + mapperPackage.getQualifiedName()
                    + " must select exactly one JdbcTypeMappings collection in package-info.java"
                    + " [Mapper=" + mapperInterface.getQualifiedName() + "]"
            ).at(mapperInterface);
        }
        AnnotationMirror selection = selections.getFirst();
        TypeMirror selectedType = annotationTypeValue(selection, "value");
        TypeElement selectedMappings = (TypeElement) typeUtils.asElement(selectedType);
        if (selectedMappings == null) {
            throw invalidJdbcTypeMappingsSelection(
                mapperInterface,
                mapperPackage.getQualifiedName().toString(),
                selectedType + " could not be resolved");
        }
        JdbcTypeMappingsValidator.ValidationResult validation =
            jdbcTypeMappingsValidator.inspect(selectedMappings);
        if (!validation.problems().isEmpty()) {
            throw invalidJdbcTypeMappingsSelection(
                mapperInterface,
                mapperPackage.getQualifiedName().toString(),
                validation.problems().getFirst().message());
        }
        List<TypeMirror> overrideTypes = annotationTypeArrayValue(selection, "overrides");
        if (overrideTypes.size() > 1) {
            throw invalidJdbcTypeMappingsSelection(
                mapperInterface,
                mapperPackage.getQualifiedName().toString(),
                "at most one override JdbcTypeMappings collection may be selected");
        }
        List<JdbcTypeMappingsValidator.MappingDeclaration> declarations = validation.declarations();
        if (!overrideTypes.isEmpty()) {
            TypeMirror overrideType = overrideTypes.getFirst();
            TypeElement overrideMappings = (TypeElement) typeUtils.asElement(overrideType);
            if (overrideMappings == null) {
                throw invalidJdbcTypeMappingsSelection(
                    mapperInterface,
                    mapperPackage.getQualifiedName().toString(),
                    overrideType + " could not be resolved");
            }
            JdbcTypeMappingsValidator.ValidationResult overrideValidation =
                jdbcTypeMappingsValidator.inspect(overrideMappings);
            if (!overrideValidation.problems().isEmpty()) {
                throw invalidJdbcTypeMappingsSelection(
                    mapperInterface,
                    mapperPackage.getQualifiedName().toString(),
                    overrideValidation.problems().getFirst().message());
            }
            declarations = mergeMappings(declarations, overrideValidation.declarations());
        }
        return new JdbcTypeMappingsSelection(
            selectedMappings, validation.declarations(), declarations);
    }

    private List<JdbcTypeMappingsValidator.MappingDeclaration> mergeMappings(
            List<JdbcTypeMappingsValidator.MappingDeclaration> baseDeclarations,
            List<JdbcTypeMappingsValidator.MappingDeclaration> overrideDeclarations) {
        List<JdbcTypeMappingsValidator.MappingDeclaration> merged = new ArrayList<>(baseDeclarations);
        for (JdbcTypeMappingsValidator.MappingDeclaration override : overrideDeclarations) {
            int existingIndex = -1;
            for (int index = 0; index < merged.size(); index++) {
                if (sameMappingKey(merged.get(index), override)) {
                    existingIndex = index;
                    break;
                }
            }
            if (existingIndex >= 0) {
                merged.set(existingIndex, override);
            } else {
                merged.add(override);
            }
        }
        return List.copyOf(merged);
    }

    private boolean sameMappingKey(
            JdbcTypeMappingsValidator.MappingDeclaration first,
            JdbcTypeMappingsValidator.MappingDeclaration second) {
        return first.javaType() != null
            && second.javaType() != null
            && typeUtils.isSameType(typeUtils.erasure(first.javaType()), typeUtils.erasure(second.javaType()))
            && first.jdbcType().equals(second.jdbcType())
            && first.vendorTypeName().equalsIgnoreCase(second.vendorTypeName());
    }

    private CompileException invalidJdbcTypeMappingsSelection(
            TypeElement mapperInterface, String packageName, String detail) {
        return new CompileException(
            "Mapper package " + packageName + " selected invalid JdbcTypeMappings collection: "
                + detail + " [Mapper=" + mapperInterface.getQualifiedName() + "]"
        ).at(mapperInterface);
    }

    private record JdbcTypeMappingsSelection(
        TypeElement type,
        List<JdbcTypeMappingsValidator.MappingDeclaration> baseDeclarations,
        List<JdbcTypeMappingsValidator.MappingDeclaration> declarations
    ) {
    }
    
    /**
     * Returns whether this pipeline can compile the interface.
     */
    public boolean supports(TypeElement mapperInterface) {
        if (mapperInterface.getAnnotation(org.liteorm.annotation.Mapper.class) != null) {
            return true;
        }
        
        return mapperInterface.getEnclosedElements().stream()
            .anyMatch(element -> element instanceof ExecutableElement &&
                (hasUseSqlProvider((ExecutableElement) element)
                    || sqlParsers.stream().anyMatch(parser -> parser.supports((ExecutableElement) element))));
    }
    
    /**
     * Analyzes inherited and declared Mapper methods.
     */
    private List<MapperCompilationModel.MethodModel> analyzeInterfaceMethods(
            TypeElement mapperInterface, JdbcTypeMappingsSelection jdbcTypeMappings)
            throws CompileException {
        Map<String, MapperCompilationModel.MethodModel> methodInfos = new LinkedHashMap<>();
        List<ResolvedMapperMethod> resolvedMethods = new ArrayList<>();

        for (var element : elementUtils.getAllMembers(mapperInterface)) {
            if (element instanceof ExecutableElement method) {
                ExecutableType resolvedMethodType = (ExecutableType) typeUtils.asMemberOf(
                    (DeclaredType) mapperInterface.asType(), method);
                resolvedMethods.add(new ResolvedMapperMethod(method, resolvedMethodType));
            }
        }
        validateNoOverloadedSqlMethods(mapperInterface, resolvedMethods);

        for (ResolvedMapperMethod resolvedMethod : resolvedMethods) {
            ExecutableElement method = resolvedMethod.method();
            try {
                MapperCompilationModel.MethodModel methodInfo = analyzeMethod(
                    mapperInterface, method, resolvedMethod.type(), jdbcTypeMappings);
                if (methodInfo != null) {
                    methodInfos.putIfAbsent(methodKey(method, resolvedMethod.type()), methodInfo);
                }
            } catch (CompileException exception) {
                throw exception.element() == null ? exception.at(method) : exception;
            }
        }
        
        return List.copyOf(methodInfos.values());
    }

    private void validateNoOverloadedSqlMethods(
            TypeElement mapperInterface, List<ResolvedMapperMethod> resolvedMethods) throws CompileException {
        Map<String, List<ResolvedMapperMethod>> methodsByName = new LinkedHashMap<>();
        XmlBasedSqlParser xmlParser = (XmlBasedSqlParser) sqlParsers.get(0);
        for (ResolvedMapperMethod method : resolvedMethods) {
            boolean defaultWithoutSql = method.method().isDefault()
                && !hasExplicitSqlDeclaration(method.method());
            boolean hasXmlResource;
            try {
                hasXmlResource = xmlParser.hasMapperResourceFile(method.method());
            } catch (RuntimeException exception) {
                throw new CompileException(
                    resolvedMethodLocation(mapperInterface, method.method(), method.type())
                        + ": failed to inspect XML SQL: " + exception.getMessage(), exception
                ).at(method.method());
            }
            if (!defaultWithoutSql
                    && (hasExplicitSqlDeclaration(method.method()) || hasXmlResource)) {
                methodsByName.computeIfAbsent(
                    method.method().getSimpleName().toString(), ignored -> new ArrayList<>()).add(method);
            }
        }
        for (var entry : methodsByName.entrySet()) {
            Map<String, ResolvedMapperMethod> distinctSignatures = new LinkedHashMap<>();
            for (ResolvedMapperMethod method : entry.getValue()) {
                distinctSignatures.putIfAbsent(resolvedSignature(method), method);
            }
            if (distinctSignatures.size() > 1) {
                List<Map.Entry<String, ResolvedMapperMethod>> overloads =
                    new ArrayList<>(distinctSignatures.entrySet());
                overloads.sort(Map.Entry.comparingByKey());
                String signatures = String.join(", ", overloads.stream().map(Map.Entry::getKey).toList());
                throw new CompileException(
                    mapperInterface.getQualifiedName() + "#" + entry.getKey()
                        + ": overloaded Mapper SQL methods are not supported: " + signatures
                ).at(overloads.get(0).getValue().method());
            }
        }
    }

    private String resolvedSignature(ResolvedMapperMethod method) {
        return method.method().getSimpleName() + "(" + String.join(", ",
            method.type().getParameterTypes().stream().map(Object::toString).toList()) + ")";
    }

    private String resolvedMethodLocation(
            TypeElement mapperInterface, ExecutableElement method, ExecutableType resolvedMethodType) {
        return mapperInterface.getQualifiedName() + "#"
            + resolvedSignature(new ResolvedMapperMethod(method, resolvedMethodType));
    }

    private String sqlSourceName(SqlContentParser parser) {
        return parser instanceof XmlBasedSqlParser ? "XML" : "annotation";
    }

    private record ResolvedMapperMethod(ExecutableElement method, ExecutableType type) {
    }

    private String methodKey(ExecutableElement method, ExecutableType resolvedMethodType) {
        return method.getSimpleName() + resolvedMethodType.getParameterTypes().toString();
    }
    
    /**
     * Analyzes one Mapper method.
     */
    private MapperCompilationModel.MethodModel analyzeMethod(
            TypeElement mapperInterface,
            ExecutableElement method,
            ExecutableType resolvedMethodType,
            JdbcTypeMappingsSelection jdbcTypeMappings)
            throws CompileException {
        if (method.isDefault() && !hasExplicitSqlDeclaration(method)) {
            return null;
        }

        ProviderBinding providerBinding = analyzeProviderBinding(mapperInterface, method, resolvedMethodType);

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
                        throw new CompileException(
                            resolvedMethodLocation(mapperInterface, method, resolvedMethodType)
                                + ": failed to parse " + sqlSourceName(parser) + " SQL: " + e.getMessage(), e);
                    }
                }
            }
        }
        
        if (sqlInfo == null && providerBinding == null) {
            XmlBasedSqlParser xmlParser = (XmlBasedSqlParser) sqlParsers.get(0);
            if (xmlParser.hasMapperResource(method)) {
                throw new CompileException(
                    resolvedMethodLocation(mapperInterface, method, resolvedMethodType)
                        + ": XML mapper exists but statement '" + method.getSimpleName() + "' was not found"
                );
            }
            return null;
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

        ExtensionBindings extensionBindings;
        try {
            extensionBindings = analyzeExtensionBindings(
                mapperInterface, method, resolvedMethodType, cursorMethod, sqlInfo, providerBinding,
                methodParameters, parameterResult.bindings(), jdbcTypeMappings);
            validateJdbcParameterTypes(
                mapperInterface, method, resolvedMethodType, cursorMethod, sqlInfo, providerBinding,
                methodParameters, parameterResult.bindings(), jdbcTypeMappings);
        } catch (IllegalArgumentException exception) {
            throw new CompileException("Failed to resolve SQL parameters for "
                + mapperInterface.getQualifiedName() + "#" + method.getSimpleName() + ": "
                + exception.getMessage(), exception);
        }
        // Build the normalized method model.
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
                extensionBindings.rowMapperFieldName() != null, generatedKeyColumn);
        }
        if (sqlInfo != null && sqlInfo.sqlType() == SqlContentParser.SqlType.BATCH) {
            validateBatchMethod(mapperInterface, method, returnType, methodParameters, sqlInfo);
        }
        ResultMappingDeclaration annotationResultMapping = annotationResultMapping(mapperInterface, method);
        ResultMappingDeclaration xmlResultMapping = xmlResultMapping(sqlInfo, returnType);
        if (annotationResultMapping.present() && xmlResultMapping.present()) {
            throw new CompileException(mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
                + ": XML resultMap cannot be combined with @Results");
        }
        ResultMappingDeclaration declaredResultMapping = xmlResultMapping.present()
            ? xmlResultMapping : annotationResultMapping;
        if (declaredResultMapping.present() && statementType != ExecutionPlan.StatementType.SELECT) {
            throw new CompileException(mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
                + ": @Results requires a SELECT method");
        }
        if (declaredResultMapping.present() && returnType.equals("void")) {
            throw new CompileException(mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
                + ": @Results requires a mapped result type");
        }
        if (declaredResultMapping.present() && extensionBindings.rowMapperFieldName() != null) {
            throw new CompileException(mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
                + ": " + (annotationResultMapping.present() ? "@Results" : "XML resultMap")
                + " cannot be combined with @UseRowMapper");
        }
        ResultMapping resultMapping = sqlInfo != null && sqlInfo.sqlType() == SqlContentParser.SqlType.BATCH
            ? new ResultMapping("", "", List.of())
            : cursorMethod != null
                ? new ResultMapping("", "", List.of())
            : extensionBindings.rowMapperFieldName() == null
                ? generateResultMapping(
                    mapperInterface, method, returnType, jdbcTypeMappings, declaredResultMapping)
                : new ResultMapping("(" + extractMappedType(returnType) + ")row[0]", "", List.of());
        extensionBindings = addDefaultResultEnumHandlers(
            resultMapping, statementType,
            cursorMethod != null || extensionBindings.rowMapperFieldName() != null,
            generatedKey, jdbcTypeMappings, extensionBindings);

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
            resultRoutingTypes(
                resultMapping, statementType,
                cursorMethod != null || extensionBindings.rowMapperFieldName() != null,
                generatedKey),
            providerBinding == null ? null : providerBinding.providerClassName(),
            providerBinding == null ? null : methodName + "SqlProvider",
            providerBinding == null ? null : providerBinding.argumentExpression(),
            extensionBindings.extensionFields(),
            extensionBindings.typeHandlerFields(),
            extensionBindings.parameterBinderFields(),
            extensionBindings.rowMapperFieldName(),
            methodParameters,
            parameterResult.bindings(),
            sqlInfo == null ? null : sqlInfo.astNode(),
            executionPlanParameterList,
            cursorMethod == null ? null : cursorMethod.parameterName()
        );
    }

    private ExtensionBindings analyzeExtensionBindings(
            TypeElement mapperInterface, ExecutableElement method, ExecutableType resolvedMethodType,
            CursorMethod cursorMethod,
            SqlContentParser.SqlParseResult sqlInfo, ProviderBinding providerBinding,
            List<SqlParameterParser.MethodParameter> methodParameters,
            List<SqlParameterParser.ParameterBinding> parameterBindings,
            JdbcTypeMappingsSelection jdbcTypeMappings) throws CompileException {
        List<MapperCompilationModel.ExtensionField> fields = new ArrayList<>();
        List<MapperCompilationModel.TypeHandlerField> typeHandlerFields = jdbcTypeMappings.declarations().stream()
            .map(mapping -> typeHandlerField(mapping, jdbcTypeMappings))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        List<String> binderFields = new ArrayList<>();
        String methodLocation = mapperInterface.getQualifiedName() + "#" + method.getSimpleName();

        boolean hasBinder = method.getParameters().stream()
            .anyMatch(parameter -> findAnnotation(parameter, "org.liteorm.annotation.UseParameterBinder") != null);
        if (hasBinder && providerBinding != null) {
            throw new CompileException(methodLocation
                + ": provider parameter binders require the typed BoundParameter contract");
        }

        java.util.Map<VariableElement, MapperCompilationModel.ExtensionField> parameterBinders =
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
            TypeElement binderElement = validateExtensionContract(
                mapperInterface, method, annotationTypeValue(annotation, "value"),
                "org.liteorm.api.ParameterBinder", resolvedMethodType.getParameterTypes().get(parameterIndex),
                "parameter binder", "parameter binder target type");
            String fieldName = method.getSimpleName() + capitalize(parameter.getSimpleName().toString())
                + "ParameterBinder";
            MapperCompilationModel.ExtensionField extensionField = new MapperCompilationModel.ExtensionField(
                binderElement.getQualifiedName().toString(), fieldName);
            parameterBinders.put(parameter, extensionField);
            addExtensionField(fields, binderElement, fieldName);
        }

        Map<String, TypeMirror> visibleParameterTypes = resolvedParameterTypes(
            method, resolvedMethodType, cursorMethod, methodParameters);
        if (sqlInfo != null && sqlInfo.sqlType() == SqlContentParser.SqlType.BATCH) {
            TypeMirror collectionType = visibleParameterTypes.values().iterator().next();
            visibleParameterTypes = new LinkedHashMap<>(Map.of(
                "item", collectionElementType(collectionType)));
        }

        if (sqlInfo != null && sqlInfo.isDynamic()) {
            Map<String, MapperCompilationModel.ExtensionField> parameterBindersByAlias =
                parameterBindersByAlias(method, cursorMethod, methodParameters, parameterBinders);
            collectDynamicParameterBinders(
                sqlInfo.astNode(), visibleParameterTypes, parameterBindersByAlias,
                typeHandlerFields, binderFields, jdbcTypeMappings,
                mapperInterface.getQualifiedName() + "." + method.getSimpleName());
        } else {
            for (SqlParameterParser.ParameterBinding binding : parameterBindings) {
                String root = binding.expression().split("\\.", 2)[0];
                VariableElement parameter = findMethodParameter(method, methodParameters, root);
                MapperCompilationModel.ExtensionField extensionField = parameterBinders.get(parameter);
                if (extensionField != null && binding.expression().contains(".")) {
                    throw new CompileException(methodLocation
                        + ": custom parameter binder must bind the whole Mapper parameter, not property "
                        + binding.expression());
                }
                if (extensionField != null) {
                    binderFields.add(extensionField.fieldName());
                    continue;
                }
                TypeMirror parameterType = resolveParameterExpressionType(
                    binding.expression(), visibleParameterTypes);
                if (selectedMapping(parameterType, binding.jdbcType(), jdbcTypeMappings) == null) {
                    addTypeHandlerField(
                        typeHandlerFields, enumTypeHandlerField(parameterType, binding.jdbcType()));
                }
                binderFields.add(routerBinderExpression(
                    parameterType, binding.jdbcType(), jdbcTypeMappings));
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
            TypeElement rowMapperElement = validateExtensionContract(
                mapperInterface, method, annotationTypeValue(rowMapperAnnotation, "value"),
                "org.liteorm.api.RowMapper", mappedTypeMirror,
                "row mapper", "row mapper target type");
            rowMapperField = method.getSimpleName() + "RowMapper";
            addExtensionField(fields, rowMapperElement, rowMapperField);
        }
        if (cursorMethod != null && rowMapperField == null) {
            throw new CompileException(methodLocation + ": cursor methods require @UseRowMapper");
        }
        return new ExtensionBindings(
            List.copyOf(fields),
            List.copyOf(typeHandlerFields),
            java.util.Collections.unmodifiableList(new ArrayList<>(binderFields)),
            rowMapperField
        );
    }

    private Map<String, MapperCompilationModel.ExtensionField> parameterBindersByAlias(
            ExecutableElement method,
            CursorMethod cursorMethod,
            List<SqlParameterParser.MethodParameter> methodParameters,
            Map<VariableElement, MapperCompilationModel.ExtensionField> parameterBinders) {
        Map<String, MapperCompilationModel.ExtensionField> bindersByAlias = new LinkedHashMap<>();
        int executionParameterIndex = 0;
        for (int parameterIndex = 0; parameterIndex < method.getParameters().size(); parameterIndex++) {
            if (cursorMethod != null && cursorMethod.parameterIndex() == parameterIndex) {
                continue;
            }
            VariableElement parameter = method.getParameters().get(parameterIndex);
            SqlParameterParser.MethodParameter description = methodParameters.get(executionParameterIndex++);
            MapperCompilationModel.ExtensionField binder = parameterBinders.get(parameter);
            if (binder != null) {
                description.aliases().forEach(alias -> bindersByAlias.put(alias, binder));
            }
        }
        return bindersByAlias;
    }

    private void collectDynamicParameterBinders(
            AstNode node,
            Map<String, TypeMirror> visibleTypes,
            Map<String, MapperCompilationModel.ExtensionField> parameterBinders,
            List<MapperCompilationModel.TypeHandlerField> typeHandlerFields,
            List<String> binderFields,
            JdbcTypeMappingsSelection jdbcTypeMappings,
            String location) throws CompileException {
        if (node instanceof AstNode.TextNode textNode) {
            Matcher matcher = HASH_PARAMETER_PATTERN.matcher(textNode.text());
            while (matcher.find()) {
                SqlParameterParser.ParameterExpression parameterExpression =
                    SqlParameterParser.parseParameterExpression(matcher.group(1));
                String expression = parameterExpression.expression();
                String root = expression.split("\\.", 2)[0];
                MapperCompilationModel.ExtensionField parameterBinder = parameterBinders.get(root);
                if (parameterBinder != null) {
                    if (expression.contains(".")) {
                        throw new CompileException(location
                            + ": custom parameter binder must bind the whole Mapper parameter, not property "
                            + expression);
                    }
                    binderFields.add(parameterBinder.fieldName());
                    continue;
                }
                TypeMirror parameterType = resolveParameterExpressionType(expression, visibleTypes);
                if (selectedMapping(
                        parameterType, parameterExpression.jdbcType(), jdbcTypeMappings) == null) {
                    addTypeHandlerField(
                        typeHandlerFields,
                        enumTypeHandlerField(parameterType, parameterExpression.jdbcType()));
                }
                binderFields.add(routerBinderExpression(
                    parameterType, parameterExpression.jdbcType(), jdbcTypeMappings));
            }
            return;
        }
        if (node instanceof AstNode.ForeachNode foreachNode) {
            String collectionRoot = foreachNode.collection().split("\\.", 2)[0];
            if (parameterBinders.containsKey(collectionRoot)) {
                throw new CompileException(location
                    + ": collection parameter binder cannot bind foreach items; bind item values explicitly");
            }
            TypeMirror collectionType = resolveParameterExpressionType(foreachNode.collection(), visibleTypes);
            Map<String, TypeMirror> foreachTypes = new LinkedHashMap<>(visibleTypes);
            foreachTypes.put(foreachNode.item(), collectionElementType(collectionType));
            Map<String, MapperCompilationModel.ExtensionField> foreachBinders =
                new LinkedHashMap<>(parameterBinders);
            foreachBinders.remove(foreachNode.item());
            for (AstNode child : foreachNode.children()) {
                collectDynamicParameterBinders(
                    child, foreachTypes, foreachBinders, typeHandlerFields,
                    binderFields, jdbcTypeMappings, location);
            }
            return;
        }

        Map<String, TypeMirror> scopedTypes = new LinkedHashMap<>(visibleTypes);
        Map<String, MapperCompilationModel.ExtensionField> scopedBinders =
            new LinkedHashMap<>(parameterBinders);
        for (AstNode child : node.getChildren()) {
            collectDynamicParameterBinders(
                child, scopedTypes, scopedBinders, typeHandlerFields,
                binderFields, jdbcTypeMappings, location);
            if (child instanceof AstNode.BindNode bindNode) {
                scopedTypes.put(bindNode.name(), null);
                scopedBinders.remove(bindNode.name());
            }
        }
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

    private TypeMirror mappedResultType(TypeMirror returnType) {
        if (returnType instanceof DeclaredType declaredType
                && declaredType.getTypeArguments().size() == 1) {
            String rawType = typeUtils.erasure(returnType).toString();
            if (rawType.equals("java.util.List") || rawType.equals("java.util.Optional")) {
                return declaredType.getTypeArguments().getFirst();
            }
        }
        return returnType;
    }

    private Map<String, TypeMirror> resolvedParameterTypes(
            ExecutableElement method,
            ExecutableType resolvedMethodType,
            CursorMethod cursorMethod,
            List<SqlParameterParser.MethodParameter> methodParameters) {
        Map<String, TypeMirror> parameterTypes = new LinkedHashMap<>();
        int executionParameterIndex = 0;
        for (int methodParameterIndex = 0;
                methodParameterIndex < method.getParameters().size();
                methodParameterIndex++) {
            if (cursorMethod != null && cursorMethod.parameterIndex() == methodParameterIndex) {
                continue;
            }
            TypeMirror parameterType = resolvedMethodType.getParameterTypes().get(methodParameterIndex);
            SqlParameterParser.MethodParameter description = methodParameters.get(executionParameterIndex++);
            for (String alias : description.aliases()) {
                parameterTypes.put(alias, parameterType);
            }
        }
        return parameterTypes;
    }

    private void validateJdbcParameterTypes(
            TypeElement mapperInterface,
            ExecutableElement method,
            ExecutableType resolvedMethodType,
            CursorMethod cursorMethod,
            SqlContentParser.SqlParseResult sqlInfo,
            ProviderBinding providerBinding,
            List<SqlParameterParser.MethodParameter> methodParameters,
            List<SqlParameterParser.ParameterBinding> parameterBindings,
            JdbcTypeMappingsSelection jdbcTypeMappings) throws CompileException {
        if (providerBinding != null) {
            return;
        }

        String location = mapperInterface.getQualifiedName() + "#" + method.getSimpleName();
        Map<String, TypeMirror> parameterTypes = new LinkedHashMap<>();
        Set<String> binderRoots = new HashSet<>();
        int executionParameterIndex = 0;
        for (int methodParameterIndex = 0; methodParameterIndex < method.getParameters().size(); methodParameterIndex++) {
            if (cursorMethod != null && cursorMethod.parameterIndex() == methodParameterIndex) {
                continue;
            }
            VariableElement parameter = method.getParameters().get(methodParameterIndex);
            TypeMirror parameterType = resolvedMethodType.getParameterTypes().get(methodParameterIndex);
            SqlParameterParser.MethodParameter description = methodParameters.get(executionParameterIndex++);
            for (String alias : description.aliases()) {
                parameterTypes.put(alias, parameterType);
                if (findAnnotation(parameter, "org.liteorm.annotation.UseParameterBinder") != null) {
                    binderRoots.add(alias);
                }
            }
        }

        if (sqlInfo.sqlType() == SqlContentParser.SqlType.BATCH) {
            TypeMirror collectionType = parameterTypes.values().iterator().next();
            TypeMirror itemType = collectionElementType(collectionType);
            parameterTypes = new LinkedHashMap<>(Map.of("item", itemType));
            binderRoots = Set.of();
        }

        if (sqlInfo.isDynamic()) {
            validateDynamicJdbcParameterTypes(
                sqlInfo.astNode(), parameterTypes, binderRoots, location, jdbcTypeMappings);
            return;
        }
        for (SqlParameterParser.ParameterBinding binding : parameterBindings) {
            validateJdbcParameterExpression(
                binding.expression(), binding.jdbcType(), parameterTypes, binderRoots, location, jdbcTypeMappings);
        }
    }

    private void validateDynamicJdbcParameterTypes(
            AstNode node,
            Map<String, TypeMirror> visibleTypes,
            Set<String> binderRoots,
            String location,
            JdbcTypeMappingsSelection jdbcTypeMappings) throws CompileException {
        if (node instanceof AstNode.TextNode textNode) {
            Matcher matcher = HASH_PARAMETER_PATTERN.matcher(textNode.text());
            while (matcher.find()) {
                SqlParameterParser.ParameterExpression parameterExpression =
                    SqlParameterParser.parseParameterExpression(matcher.group(1));
                validateJdbcParameterExpression(
                    parameterExpression.expression(), parameterExpression.jdbcType(),
                    visibleTypes, binderRoots, location, jdbcTypeMappings);
            }
            return;
        }
        if (node instanceof AstNode.ForeachNode foreachNode) {
            TypeMirror collectionType = resolveParameterExpressionType(foreachNode.collection(), visibleTypes);
            Map<String, TypeMirror> foreachTypes = new LinkedHashMap<>(visibleTypes);
            foreachTypes.put(foreachNode.item(), collectionElementType(collectionType));
            for (AstNode child : foreachNode.children()) {
                validateDynamicJdbcParameterTypes(
                    child, foreachTypes, binderRoots, location, jdbcTypeMappings);
            }
            return;
        }

        Map<String, TypeMirror> scopedTypes = new LinkedHashMap<>(visibleTypes);
        for (AstNode child : node.getChildren()) {
            validateDynamicJdbcParameterTypes(
                child, scopedTypes, binderRoots, location, jdbcTypeMappings);
            if (child instanceof AstNode.BindNode bindNode) {
                scopedTypes.put(bindNode.name(), null);
            }
        }
    }

    private void validateJdbcParameterExpression(
            String expression,
            String declaredJdbcType,
            Map<String, TypeMirror> visibleTypes,
            Set<String> binderRoots,
            String location,
            JdbcTypeMappingsSelection jdbcTypeMappings) throws CompileException {
        String root = expression.split("\\.", 2)[0];
        if (binderRoots.contains(root) && !expression.contains(".")) {
            return;
        }
        TypeMirror parameterType = resolveParameterExpressionType(expression, visibleTypes);
        List<JdbcTypeMappingsValidator.MappingDeclaration> matchingMappings =
            matchingMappings(parameterType, jdbcTypeMappings);
        JdbcTypeMappingsValidator.MappingDeclaration selectedMapping =
            selectedMapping(parameterType, declaredJdbcType, jdbcTypeMappings);
        if (declaredJdbcType != null
                && selectedMapping == null
                && enumTypeHandlerField(parameterType, declaredJdbcType) == null) {
            throw new CompileException(location + ": no JDBC type mapping exists for "
                + parameterType + " + " + declaredJdbcType);
        }
        if (declaredJdbcType == null && matchingMappings.size() > 1
                && selectedMapping == null
                && !usesDefaultCanonicalJdbcBinding(parameterType)) {
            String jdbcTypes = matchingMappings.stream()
                .map(JdbcTypeMappingsValidator.MappingDeclaration::jdbcType)
                .collect(java.util.stream.Collectors.joining(", ", "[", "]"));
            throw new CompileException(location + ": JDBC parameter expression " + expression + " of type "
                + parameterType + " matches multiple JDBC type mappings " + jdbcTypes
                + "; declare jdbcType explicitly");
        }
        if (parameterType == null || isSupportedJdbcParameterType(parameterType)
                || selectedMapping != null) {
            return;
        }
        throw new CompileException(location + ": JDBC parameter expression " + expression + " has unsupported type "
            + parameterType + " and requires @UseParameterBinder");
    }

    private TypeMirror resolveParameterExpressionType(String expression, Map<String, TypeMirror> visibleTypes) {
        String[] parts = expression.split("\\.");
        TypeMirror currentType = visibleTypes.get(parts[0]);
        if (currentType == null) {
            return null;
        }
        for (int index = 1; index < parts.length; index++) {
            String property = parts[index];
            if (currentType.getKind() == TypeKind.ARRAY && "length".equals(property)) {
                return typeUtils.getPrimitiveType(TypeKind.INT);
            }
            if (!(currentType instanceof DeclaredType declaredType)) {
                return null;
            }
            ExecutableElement accessor = declaredType.asElement().getEnclosedElements().stream()
                .filter(element -> element.getKind() == javax.lang.model.element.ElementKind.METHOD)
                .map(ExecutableElement.class::cast)
                .filter(method -> method.getSimpleName().contentEquals(property))
                .filter(method -> method.getParameters().isEmpty())
                .findFirst()
                .orElse(null);
            if (accessor == null) {
                return null;
            }
            currentType = ((ExecutableType) typeUtils.asMemberOf(declaredType, accessor)).getReturnType();
        }
        return currentType;
    }

    private TypeMirror collectionElementType(TypeMirror collectionType) {
        if (collectionType instanceof ArrayType arrayType) {
            return arrayType.getComponentType();
        }
        if (collectionType instanceof DeclaredType declaredType && !declaredType.getTypeArguments().isEmpty()) {
            return declaredType.getTypeArguments().get(0);
        }
        return elementUtils.getTypeElement("java.lang.Object").asType();
    }

    private boolean isSupportedJdbcParameterType(TypeMirror parameterType) {
        if (parameterType.getKind().isPrimitive()) {
            return true;
        }
        if (parameterType.getKind() == TypeKind.ARRAY) {
            return ((ArrayType) parameterType).getComponentType().getKind() == TypeKind.BYTE;
        }
        if (parameterType instanceof DeclaredType declaredType
                && declaredType.asElement().getKind() == javax.lang.model.element.ElementKind.ENUM) {
            return true;
        }
        return switch (typeUtils.erasure(parameterType).toString()) {
            case "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long",
                "java.lang.Float", "java.lang.Double", "java.lang.Boolean", "java.lang.Character",
                "java.lang.String", "java.math.BigDecimal", "java.time.LocalDate", "java.time.LocalDateTime",
                "java.time.Instant", "java.util.UUID", "java.time.LocalTime", "java.time.OffsetDateTime" -> true;
            default -> false;
        };
    }

    private JdbcTypeMappingsValidator.MappingDeclaration selectedMapping(
            TypeMirror javaType, JdbcTypeMappingsSelection jdbcTypeMappings) {
        return selectedMapping(javaType, null, jdbcTypeMappings);
    }

    private JdbcTypeMappingsValidator.MappingDeclaration selectedMapping(
            TypeMirror javaType,
            String declaredJdbcType,
            JdbcTypeMappingsSelection jdbcTypeMappings) {
        List<JdbcTypeMappingsValidator.MappingDeclaration> matches = matchingMappings(javaType, jdbcTypeMappings)
            .stream()
            .filter(mapping -> declaredJdbcType == null || declaredJdbcType.equals(mapping.jdbcType()))
            .toList();
        if (declaredJdbcType != null) {
            return selectParameterMapping(matches);
        }
        String canonicalJdbcType = canonicalJdbcType(javaType, jdbcTypeMappings);
        List<JdbcTypeMappingsValidator.MappingDeclaration> canonicalMatches = matches.stream()
            .filter(mapping -> mapping.jdbcType().equals(canonicalJdbcType))
            .toList();
        return selectParameterMapping(canonicalMatches);
    }

    private JdbcTypeMappingsValidator.MappingDeclaration selectParameterMapping(
            List<JdbcTypeMappingsValidator.MappingDeclaration> matches) {
        List<JdbcTypeMappingsValidator.MappingDeclaration> genericMatches = matches.stream()
            .filter(mapping -> mapping.vendorTypeName() == null || mapping.vendorTypeName().isBlank())
            .toList();
        if (genericMatches.size() == 1) {
            return genericMatches.getFirst();
        }
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private String canonicalJdbcType(
            TypeMirror javaType, JdbcTypeMappingsSelection jdbcTypeMappings) {
        List<JdbcTypeMappingsValidator.MappingDeclaration> baseMatches =
            matchingMappings(javaType, jdbcTypeMappings.baseDeclarations());
        List<String> baseJdbcTypes = baseMatches.stream()
            .map(JdbcTypeMappingsValidator.MappingDeclaration::jdbcType)
            .distinct()
            .toList();
        if (baseJdbcTypes.size() == 1) {
            return baseJdbcTypes.getFirst();
        }
        String standardCanonicalJdbcType = canonicalJdbcType(javaType);
        return baseJdbcTypes.contains(standardCanonicalJdbcType) || baseJdbcTypes.isEmpty()
            ? standardCanonicalJdbcType
            : null;
    }

    private String routerBinderExpression(
            TypeMirror javaType,
            String declaredJdbcType,
            JdbcTypeMappingsSelection jdbcTypeMappings) {
        if (javaType == null) {
            return null;
        }
        String jdbcType = declaredJdbcType == null
            ? canonicalJdbcType(javaType, jdbcTypeMappings) : declaredJdbcType;
        String jdbcExpression = jdbcType == null
            ? "null" : "java.sql.JDBCType." + jdbcType;
        return "typeHandlerManager.parameterBinder(" + typeClassLiteral(javaType) + ", "
            + jdbcExpression + ")";
    }

    private String typeClassLiteral(TypeMirror type) {
        return typeUtils.erasure(type) + ".class";
    }

    private List<String> resultRoutingTypes(
            ResultMapping resultMapping,
            ExecutionPlan.StatementType statementType,
            boolean customRowMapper,
            boolean generatedKey) {
        if (customRowMapper || statementType == ExecutionPlan.StatementType.BATCH
                || statementType == ExecutionPlan.StatementType.UPDATE
                || statementType == ExecutionPlan.StatementType.DELETE
                || statementType == ExecutionPlan.StatementType.INSERT && !generatedKey) {
            return List.of();
        }
        return resultMapping.properties().stream()
            .map(property -> property.targetJavaType() + ".class")
            .toList();
    }

    private ExtensionBindings addDefaultResultEnumHandlers(
            ResultMapping resultMapping,
            ExecutionPlan.StatementType statementType,
            boolean customRowMapper,
            boolean generatedKey,
            JdbcTypeMappingsSelection jdbcTypeMappings,
            ExtensionBindings bindings) {
        if (customRowMapper || statementType == ExecutionPlan.StatementType.BATCH
                || statementType == ExecutionPlan.StatementType.UPDATE
                || statementType == ExecutionPlan.StatementType.DELETE
                || statementType == ExecutionPlan.StatementType.INSERT && !generatedKey) {
            return bindings;
        }
        List<MapperCompilationModel.TypeHandlerField> fields =
            new ArrayList<>(bindings.typeHandlerFields());
        resultMapping.properties().stream()
            .map(NormalizedResultProperty::targetJavaType)
            .map(elementUtils::getTypeElement)
            .filter(java.util.Objects::nonNull)
            .map(TypeElement::asType)
            .forEach(type -> addEnumHandlers(fields, type, jdbcTypeMappings));
        return new ExtensionBindings(
            bindings.extensionFields(), List.copyOf(fields),
            bindings.parameterBinderFields(), bindings.rowMapperFieldName());
    }

    private void addEnumHandlers(
            List<MapperCompilationModel.TypeHandlerField> fields,
            TypeMirror javaType,
            JdbcTypeMappingsSelection jdbcTypeMappings) {
        if (!isEnumType(javaType) || hasSelectedMapping(javaType, jdbcTypeMappings)) {
            return;
        }
        addTypeHandlerField(fields, enumTypeHandlerField(javaType, "VARCHAR"));
        addTypeHandlerField(fields, enumTypeHandlerField(javaType, "INTEGER"));
    }

    private String canonicalJdbcType(TypeMirror javaType) {
        if (javaType == null) {
            return null;
        }
        if (javaType.getKind() == TypeKind.ARRAY) {
            TypeMirror componentType = ((ArrayType) javaType).getComponentType();
            if (componentType.getKind() == TypeKind.BYTE
                    || "java.lang.Byte".equals(componentType.toString())) {
                return "VARBINARY";
            }
        }
        if (javaType instanceof DeclaredType declaredType
                && declaredType.asElement().getKind() == javax.lang.model.element.ElementKind.ENUM) {
            return "VARCHAR";
        }
        return switch (javaType.toString()) {
            case "byte", "java.lang.Byte" -> "TINYINT";
            case "short", "java.lang.Short" -> "SMALLINT";
            case "int", "java.lang.Integer", "java.time.Year", "java.time.Month" -> "INTEGER";
            case "long", "java.lang.Long" -> "BIGINT";
            case "float", "java.lang.Float" -> "FLOAT";
            case "double", "java.lang.Double" -> "DOUBLE";
            case "boolean", "java.lang.Boolean" -> "BOOLEAN";
            case "char", "java.lang.Character" -> "CHAR";
            case "java.lang.String", "java.time.YearMonth" -> "VARCHAR";
            case "java.math.BigDecimal", "java.math.BigInteger" -> "DECIMAL";
            case "java.time.LocalDate", "java.sql.Date", "java.time.chrono.JapaneseDate" -> "DATE";
            case "java.time.LocalTime", "java.sql.Time" -> "TIME";
            case "java.time.LocalDateTime", "java.time.Instant", "java.util.Date", "java.sql.Timestamp" ->
                "TIMESTAMP";
            case "java.time.OffsetDateTime" -> "TIMESTAMP_WITH_TIMEZONE";
            case "java.util.UUID" -> "OTHER";
            default -> null;
        };
    }

    private boolean usesDefaultCanonicalJdbcBinding(TypeMirror javaType) {
        if (javaType == null || javaType.getKind().isPrimitive()) {
            return javaType != null;
        }
        if (javaType.getKind() == TypeKind.ARRAY) {
            return ((ArrayType) javaType).getComponentType().getKind() == TypeKind.BYTE;
        }
        return switch (javaType.toString()) {
            case "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long",
                "java.lang.Float", "java.lang.Double", "java.lang.Boolean", "java.lang.Character",
                "java.lang.String", "java.math.BigDecimal", "java.time.LocalDate",
                "java.time.LocalDateTime", "java.time.Instant" -> true;
            default -> false;
        };
    }

    private List<JdbcTypeMappingsValidator.MappingDeclaration> matchingMappings(
            TypeMirror javaType, JdbcTypeMappingsSelection jdbcTypeMappings) {
        return matchingMappings(javaType, jdbcTypeMappings.declarations());
    }

    private List<JdbcTypeMappingsValidator.MappingDeclaration> matchingMappings(
            TypeMirror javaType,
            List<JdbcTypeMappingsValidator.MappingDeclaration> declarations) {
        if (javaType == null) {
            return List.of();
        }
        return declarations.stream()
            .filter(mapping -> mapping.javaType() != null)
            .filter(mapping -> typeUtils.isSameType(
                typeUtils.erasure(mapping.javaType()), typeUtils.erasure(javaType)))
            .toList();
    }

    private MapperCompilationModel.TypeHandlerField typeHandlerField(
            JdbcTypeMappingsValidator.MappingDeclaration mapping,
            JdbcTypeMappingsSelection jdbcTypeMappings) {
        TypeElement handlerElement = (TypeElement) typeUtils.asElement(mapping.handlerType());
        String javaTypeName = mapping.javaType().toString();
        int declarationId = jdbcTypeMappings.declarations().indexOf(mapping) + 1;
        return new MapperCompilationModel.TypeHandlerField(
            handlerElement.getQualifiedName().toString(),
            javaTypeName,
            "typeHandler" + declarationId,
            "jdbcValueParameterBinder" + declarationId,
            "bindJdbcValue" + declarationId,
            mapping.jdbcType(),
            mapping.vendorTypeName(),
            null
        );
    }

    private MapperCompilationModel.TypeHandlerField enumTypeHandlerField(
            TypeMirror javaType, String declaredJdbcType) {
        if (!isEnumType(javaType)) {
            return null;
        }
        String jdbcType = declaredJdbcType == null ? "VARCHAR" : declaredJdbcType;
        String handlerName;
        if ("VARCHAR".equals(jdbcType)) {
            handlerName = "EnumNameTypeHandler";
        } else if ("INTEGER".equals(jdbcType)) {
            handlerName = "EnumOrdinalTypeHandler";
        } else {
            return null;
        }
        String javaTypeName = javaType.toString();
        String identifier = javaTypeName.replaceAll("[^A-Za-z0-9]", "_") + "_" + jdbcType.toLowerCase();
        String rawHandlerType = "org.liteorm.jdbc.StandardJdbcTypeMappings." + handlerName;
        String handlerType = rawHandlerType + "<" + javaTypeName + ">";
        String constructorArgument = "VARCHAR".equals(jdbcType)
            ? javaTypeName + "::valueOf" : javaTypeName + ".values()";
        return new MapperCompilationModel.TypeHandlerField(
            handlerType,
            javaTypeName,
            "enumTypeHandler_" + identifier,
            "enumJdbcValueParameterBinder_" + identifier,
            "bindEnumJdbcValue_" + identifier,
            jdbcType,
            null,
            "new " + rawHandlerType + "<>(" + constructorArgument + ")"
        );
    }

    private boolean isEnumType(TypeMirror javaType) {
        return javaType instanceof DeclaredType declaredType
            && declaredType.asElement().getKind() == javax.lang.model.element.ElementKind.ENUM;
    }

    private void addTypeHandlerField(
            List<MapperCompilationModel.TypeHandlerField> fields,
            MapperCompilationModel.TypeHandlerField field) {
        if (field != null && fields.stream().noneMatch(existing -> existing.fieldName().equals(field.fieldName()))) {
            fields.add(field);
        }
    }

    private TypeElement validateExtensionContract(
            TypeElement mapperInterface, ExecutableElement method, TypeMirror handlerType,
            String interfaceName, TypeMirror expectedTarget,
            String contractName, String mismatchLabel) throws CompileException {
        TypeElement extensionElement = (TypeElement) typeUtils.asElement(handlerType);
        String location = mapperInterface.getQualifiedName() + "#" + method.getSimpleName();
        if (extensionElement == null) {
            throw new CompileException(location + ": " + contractName + " type could not be resolved");
        }
        String mapperPackage = elementUtils.getPackageOf(mapperInterface).getQualifiedName().toString();
        boolean samePackage = mapperPackage.equals(
            elementUtils.getPackageOf(extensionElement).getQualifiedName().toString());
        if (extensionElement.getModifiers().contains(Modifier.ABSTRACT)
                || extensionElement.getModifiers().contains(Modifier.PRIVATE)
                || (!samePackage && !extensionElement.getModifiers().contains(Modifier.PUBLIC))) {
            throw new CompileException(location + ": " + contractName + " must be a concrete accessible class");
        }
        boolean hasNoArgConstructor = extensionElement.getEnclosedElements().stream()
            .filter(element -> element.getKind() == javax.lang.model.element.ElementKind.CONSTRUCTOR)
            .map(ExecutableElement.class::cast)
            .anyMatch(constructor -> constructor.getParameters().isEmpty()
                && (constructor.getModifiers().contains(Modifier.PUBLIC)
                    || (samePackage && !constructor.getModifiers().contains(Modifier.PRIVATE))));
        if (!hasNoArgConstructor) {
            throw new CompileException(location + ": " + contractName
                + " requires an accessible no-arg constructor");
        }
        TypeMirror actualTarget = findGenericInterfaceInput(extensionElement, interfaceName);
        if (actualTarget == null) {
            throw new CompileException(location + ": " + contractName
                + " must implement " + interfaceName + "<T>");
        }
        if (!typeUtils.isSameType(typeUtils.erasure(actualTarget), typeUtils.erasure(expectedTarget))) {
            throw new CompileException(location + ": " + mismatchLabel + " " + actualTarget
                + " does not match " + expectedTarget);
        }
        return extensionElement;
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

    private void addExtensionField(
            List<MapperCompilationModel.ExtensionField> fields, TypeElement extensionElement, String fieldName) {
        if (fields.stream().noneMatch(field -> field.fieldName().equals(fieldName))) {
            fields.add(new MapperCompilationModel.ExtensionField(
                extensionElement.getQualifiedName().toString(), fieldName));
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

    private record ExtensionBindings(
        List<MapperCompilationModel.ExtensionField> extensionFields,
        List<MapperCompilationModel.TypeHandlerField> typeHandlerFields,
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

    private List<TypeMirror> annotationTypeArrayValue(AnnotationMirror annotation, String name) {
        Object value = annotationValue(annotation, name).getValue();
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
            .map(AnnotationValue.class::cast)
            .map(AnnotationValue::getValue)
            .map(TypeMirror.class::cast)
            .toList();
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
                "java.math.BigDecimal", "java.math.BigInteger", "java.lang.String" -> true;
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
     * Builds the generated Java parameter list.
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
     * Generates result-mapping source.
     */
    private ResultMapping generateResultMapping(
            TypeElement mapperInterface,
            ExecutableElement method,
            String returnType,
            JdbcTypeMappingsSelection jdbcTypeMappings,
            ResultMappingDeclaration declaredResultMapping) throws CompileException {
        if (returnType.contains("List<")) {
            String elementType = extractListElementType(returnType);
            return generateSingleMapping(
                mapperInterface, method, elementType, jdbcTypeMappings, declaredResultMapping);
        } else if (returnType.startsWith("java.util.Optional<")) {
            return generateSingleMapping(
                mapperInterface, method, extractOptionalElementType(returnType),
                jdbcTypeMappings, declaredResultMapping);
        } else if (!returnType.equals("void")) {
            return generateSingleMapping(
                mapperInterface, method, returnType, jdbcTypeMappings, declaredResultMapping);
        } else {
            return new ResultMapping("", "", List.of());
        }
    }
    
    /**
     * Extracts a {@link List} element type.
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
     * Generates mapping source for one result object.
     */
    private ResultMapping generateSingleMapping(
            TypeElement mapperInterface,
            ExecutableElement method,
            String objectType,
            JdbcTypeMappingsSelection jdbcTypeMappings,
            ResultMappingDeclaration declaredResultMapping) throws CompileException {
        String scalarMapping = generateValueMapping(objectType, "row[0]");
        if (scalarMapping != null) {
            return declaredScalarMapping(
                mapperInterface, method, objectType, scalarMapping, declaredResultMapping);
        }

        var typeElement = elementUtils.getTypeElement(objectType);
        if (typeElement == null) {
            throw unsupportedResultMapping(mapperInterface, method, objectType);
        }

        if (hasSelectedMapping(typeElement.asType(), jdbcTypeMappings)) {
            return declaredScalarMapping(
                mapperInterface, method, objectType, "(" + objectType + ")row[0]",
                declaredResultMapping);
        }

        ResultProperty blankProperty = declaredResultMapping.properties().stream()
            .filter(property -> property.property().isBlank())
            .findFirst()
            .orElse(null);
        if (blankProperty != null) {
            String mappingName = blankProperty.source() == ResultMappingSource.ANNOTATION
                ? "@Result" : "XML result mapping";
            throw new CompileException(blankProperty.sourceLocation() + ": " + mappingName
                + " property must not be blank for an object result");
        }

        if (typeElement.getKind() == javax.lang.model.element.ElementKind.RECORD) {
            return generateRecordMapping(
                mapperInterface, method, typeElement, objectType,
                jdbcTypeMappings, declaredResultMapping);
        }

        return generateJavaBeanMapping(
            mapperInterface, method, typeElement, objectType,
            jdbcTypeMappings, declaredResultMapping);
    }

    private ResultMapping declaredScalarMapping(
            TypeElement mapperInterface,
            ExecutableElement method,
            String objectType,
            String expression,
            ResultMappingDeclaration declaredResultMapping) throws CompileException {
        if (!declaredResultMapping.present()) {
            return new ResultMapping(
                expression, "", List.of(new NormalizedResultProperty(
                    "", "", objectType, false, -1, ResultMappingSource.CONVENTION,
                    mapperInterface.getQualifiedName() + "#" + method.getSimpleName())));
        }
        if (declaredResultMapping.properties().size() != 1
                || !declaredResultMapping.properties().getFirst().property().isBlank()) {
            String mappingName = declaredResultMapping.properties().stream()
                    .allMatch(property -> property.source() == ResultMappingSource.ANNOTATION)
                ? "@Results" : "XML resultMap";
            throw new CompileException(mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
                + ": scalar " + mappingName + " requires exactly one mapping with a blank property");
        }
        validateDeclaredJavaType(declaredResultMapping.properties().getFirst(), objectType);
        ResultProperty property = declaredResultMapping.properties().getFirst();
        return new ResultMapping(
            expression, "", List.of(new NormalizedResultProperty(
                "", property.column(), objectType, false, -1,
                property.source(), property.sourceLocation())));
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
            case "java.math.BigInteger" -> "ResultValueConverters.toBigInteger(" + valueExpression + ")";
            case "java.lang.Boolean", "boolean" -> "ResultValueConverters.toBoolean(" + valueExpression + ")";
            case "java.lang.Character", "char" -> "ResultValueConverters.toCharacter(" + valueExpression + ")";
            case "java.time.LocalDate" -> "ResultValueConverters.toLocalDate(" + valueExpression + ")";
            case "java.time.LocalDateTime" -> "ResultValueConverters.toLocalDateTime(" + valueExpression + ")";
            case "java.time.Instant" -> "ResultValueConverters.toInstant(" + valueExpression + ")";
            case "java.util.UUID" -> "ResultValueConverters.toUuid(" + valueExpression + ")";
            case "java.time.LocalTime" -> "ResultValueConverters.toLocalTime(" + valueExpression + ")";
            case "java.time.OffsetDateTime" -> "ResultValueConverters.toOffsetDateTime(" + valueExpression + ")";
            case "byte[]" -> "(byte[])" + valueExpression;
            case "java.lang.Byte[]" -> "ResultValueConverters.toBoxedBytes(" + valueExpression + ")";
            case "java.util.Date" -> "ResultValueConverters.toUtilDate(" + valueExpression + ")";
            case "java.sql.Date" -> "ResultValueConverters.toSqlDate(" + valueExpression + ")";
            case "java.sql.Time" -> "ResultValueConverters.toSqlTime(" + valueExpression + ")";
            case "java.sql.Timestamp" -> "ResultValueConverters.toSqlTimestamp(" + valueExpression + ")";
            case "java.time.Year" -> "ResultValueConverters.toYear(" + valueExpression + ")";
            case "java.time.Month" -> "ResultValueConverters.toMonth(" + valueExpression + ")";
            case "java.time.YearMonth" -> "ResultValueConverters.toYearMonth(" + valueExpression + ")";
            case "java.time.chrono.JapaneseDate" ->
                "ResultValueConverters.toJapaneseDate(" + valueExpression + ")";
            default -> null;
        };
    }

    private String generateValueMapping(String objectType, String valueExpression) {
        return generateValueMapping(objectType, valueExpression, null);
    }

    private String generateValueMapping(
            String objectType, String valueExpression, String declaredJdbcType) {
        String scalarMapping = generateScalarMapping(objectType, valueExpression);
        if (scalarMapping != null) {
            return declaredJdbcType == null ? scalarMapping : null;
        }
        TypeElement typeElement = elementUtils.getTypeElement(objectType);
        if (typeElement != null && typeElement.getKind() == javax.lang.model.element.ElementKind.ENUM) {
            if ("INTEGER".equals(declaredJdbcType)) {
                return "ResultValueConverters.toEnumOrdinal(" + valueExpression + ", "
                    + objectType + ".class)";
            }
            if (declaredJdbcType != null && !"VARCHAR".equals(declaredJdbcType)) {
                return null;
            }
            return valueExpression + " == null ? null : " + valueExpression + " instanceof " + objectType
                + " ? (" + objectType + ")" + valueExpression + " : " + objectType + ".valueOf("
                + valueExpression + ".toString())";
        }
        return null;
    }
    
    /**
     * Generates direct record construction source.
     */
    private ResultMapping generateRecordMapping(
            TypeElement mapperInterface, ExecutableElement mapperMethod,
            TypeElement typeElement,
            String objectType,
            JdbcTypeMappingsSelection jdbcTypeMappings,
            ResultMappingDeclaration declaredResultMapping) throws CompileException {
        var recordComponents = typeElement.getRecordComponents();
        List<NormalizedResultProperty> normalizedProperties = new ArrayList<>(recordComponents.size());
        Map<String, ResultProperty> declaredProperties = declaredResultMapping.byProperty();
        
        StringBuilder mapping = new StringBuilder("new " + objectType + "(");
        for (int i = 0; i < recordComponents.size(); i++) {
            var component = recordComponents.get(i);
            String componentName = component.getSimpleName().toString();
            String componentType = component.asType().toString();
            ResultProperty declaredProperty = declaredProperties.remove(componentName);
            if (declaredProperty != null
                    && declaredProperty.source() == ResultMappingSource.XML
                    && !declaredProperty.constructorArgument()) {
                throw new CompileException(declaredProperty.sourceLocation()
                    + ": record component mappings must be declared inside <constructor>");
            }
            validateDeclaredJavaType(declaredProperty, component.asType());
            normalizedProperties.add(normalizedResultProperty(
                componentName,
                declaredProperty == null ? componentName : declaredProperty.column(),
                componentType, true, i, declaredProperty,
                objectType + "." + componentName));
            
            if (i > 0) {
                mapping.append(", ");
            }
            
            String convertedValue = generateValueMapping(
                componentType, "row[resultColumnIndexes[" + i + "]]");
            if (convertedValue == null && hasSelectedMapping(component.asType(), jdbcTypeMappings)) {
                convertedValue = "(" + componentType + ")row[resultColumnIndexes[" + i + "]]";
            }
            if (convertedValue == null) {
                throw unsupportedResultMapping(mapperInterface, mapperMethod,
                    "nested record component " + objectType + "." + componentName
                        + " (" + componentType + ")");
            }
            mapping.append(convertedValue);
        }
        mapping.append(")");
        rejectUnknownResultProperties(objectType, declaredProperties);
        
        return new ResultMapping(mapping.toString(), "", List.copyOf(normalizedProperties));
    }
    
    /**
     * Generates JavaBean mapping source.
     */
    private ResultMapping generateJavaBeanMapping(
            TypeElement mapperInterface, ExecutableElement mapperMethod,
            TypeElement typeElement,
            String objectType,
            JdbcTypeMappingsSelection jdbcTypeMappings,
            ResultMappingDeclaration declaredResultMapping) throws CompileException {
        boolean hasAccessibleNoArgConstructor = typeElement.getEnclosedElements().stream()
            .filter(element -> element.getKind() == javax.lang.model.element.ElementKind.CONSTRUCTOR)
            .map(ExecutableElement.class::cast)
            .anyMatch(constructor -> constructor.getParameters().isEmpty()
                && !constructor.getModifiers().contains(Modifier.PRIVATE));
        if (!hasAccessibleNoArgConstructor) {
            throw unsupportedResultMapping(mapperInterface, mapperMethod, objectType
                + " requires an accessible no-arg constructor");
        }

        Map<String, ExecutableElement> settersByProperty = new LinkedHashMap<>();
        for (Element element : typeElement.getEnclosedElements()) {
            if (element.getKind() != javax.lang.model.element.ElementKind.METHOD) {
                continue;
            }
            ExecutableElement setter = (ExecutableElement) element;
            String setterName = setter.getSimpleName().toString();
            if (!setter.getModifiers().contains(Modifier.PUBLIC)
                    || setter.getModifiers().contains(Modifier.STATIC)
                    || setter.getParameters().size() != 1
                    || !setterName.startsWith("set")
                    || setterName.length() == 3) {
                continue;
            }
            String property = javaBeanPropertyName(setterName);
            if (settersByProperty.putIfAbsent(property, setter) != null) {
                throw unsupportedResultMapping(mapperInterface, mapperMethod,
                    objectType + " has overloaded public setters for property " + property);
            }
        }
        if (settersByProperty.isEmpty()) {
            throw unsupportedResultMapping(
                mapperInterface, mapperMethod, objectType + " has no writable JavaBean properties");
        }

        String helperName = "map" + Character.toUpperCase(mapperMethod.getSimpleName().charAt(0))
            + mapperMethod.getSimpleName().toString().substring(1) + "Row";
        StringBuilder helper = new StringBuilder();
        helper.append("    private ").append(objectType).append(" ").append(helperName)
            .append("(Object[] row, int[] resultColumnIndexes) {\n");
        helper.append("        ").append(objectType).append(" mapped = new ").append(objectType).append("();\n");
        List<NormalizedResultProperty> normalizedProperties = new ArrayList<>(settersByProperty.size());
        Map<String, ResultProperty> declaredProperties = declaredResultMapping.byProperty();

        int index = 0;
        for (Map.Entry<String, ExecutableElement> setterEntry : settersByProperty.entrySet()) {
            String property = setterEntry.getKey();
            ExecutableElement setter = setterEntry.getValue();
            ResultProperty declaredProperty = declaredProperties.remove(property);
            if (declaredProperty != null && declaredProperty.constructorArgument()) {
                throw new CompileException(declaredProperty.sourceLocation()
                    + ": JavaBean property mappings cannot use <constructor>");
            }
            String parameterType = setter.getParameters().get(0).asType().toString();
            validateDeclaredJavaType(declaredProperty, setter.getParameters().get(0).asType());
            normalizedProperties.add(normalizedResultProperty(
                property,
                declaredProperty == null ? property : declaredProperty.column(),
                parameterType, false, -1, declaredProperty,
                objectType + "." + property));
            String convertedValue = generateValueMapping(
                parameterType, "row[resultColumnIndexes[" + index + "]]");
            if (convertedValue == null
                    && hasSelectedMapping(setter.getParameters().getFirst().asType(), jdbcTypeMappings)) {
                convertedValue = "(" + parameterType + ")row[resultColumnIndexes[" + index + "]]";
            }
            if (convertedValue == null) {
                throw unsupportedResultMapping(mapperInterface, mapperMethod,
                    "nested object property " + objectType + "." + property + " (" + parameterType + ")");
            }
            helper.append("        mapped.").append(setter.getSimpleName()).append("(")
                .append(convertedValue).append(");\n");
            index++;
        }
        rejectUnknownResultProperties(objectType, declaredProperties);
        helper.append("        return mapped;\n");
        helper.append("    }\n");
        return new ResultMapping(
            helperName + "(row, resultColumnIndexes)", helper.toString(),
            List.copyOf(normalizedProperties));
    }

    private String javaBeanPropertyName(String setterName) {
        String property = setterName.substring(3);
        if (property.length() > 1
                && Character.isUpperCase(property.charAt(0))
                && Character.isUpperCase(property.charAt(1))) {
            return property;
        }
        return Character.toLowerCase(property.charAt(0)) + property.substring(1);
    }

    private NormalizedResultProperty normalizedResultProperty(
            String property,
            String column,
            String targetJavaType,
            boolean constructorArgument,
            int constructorIndex,
            ResultProperty declaredProperty,
            String conventionLocation) {
        return new NormalizedResultProperty(
            property, column, targetJavaType, constructorArgument, constructorIndex,
            declaredProperty == null ? ResultMappingSource.CONVENTION : declaredProperty.source(),
            declaredProperty == null ? conventionLocation : declaredProperty.sourceLocation());
    }

    private boolean hasSelectedMapping(
            TypeMirror javaType, JdbcTypeMappingsSelection jdbcTypeMappings) {
        return !matchingMappings(javaType, jdbcTypeMappings).isEmpty();
    }

    private ResultMappingDeclaration annotationResultMapping(
            TypeElement mapperInterface, ExecutableElement method) throws CompileException {
        AnnotationMirror results = findAnnotation(method, "org.liteorm.annotation.Results");
        if (results == null) {
            return ResultMappingDeclaration.none();
        }
        Object rawValue = annotationValue(results, "value").getValue();
        if (!(rawValue instanceof List<?> values) || values.isEmpty()) {
            throw new CompileException(mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
                + ": @Results must declare at least one @Result");
        }
        List<ResultProperty> properties = new ArrayList<>(values.size());
        Set<String> declaredNames = new HashSet<>();
        boolean scalar = values.size() == 1;
        int resultIndex = 0;
        for (Object rawResult : values) {
            AnnotationMirror result = (AnnotationMirror) ((AnnotationValue) rawResult).getValue();
            String sourceLocation = mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
                + " @Result[" + resultIndex + "]";
            String column = ((String) annotationValue(result, "column").getValue()).trim();
            String property = ((String) annotationValue(result, "property").getValue()).trim();
            if (column.isEmpty()) {
                throw new CompileException(sourceLocation + ": @Result column must not be blank");
            }
            if (property.isEmpty() && !scalar) {
                throw new CompileException(
                    sourceLocation + ": @Result property must not be blank for an object result");
            }
            if (!declaredNames.add(property)) {
                throw new CompileException(
                    sourceLocation + ": duplicate @Result property '" + property + "'");
            }
            TypeMirror declaredJavaType = (TypeMirror) annotationValue(result, "javaType").getValue();
            properties.add(new ResultProperty(
                property, column,
                declaredJavaType.getKind() == TypeKind.VOID ? null : declaredJavaType.toString(),
                false, -1, ResultMappingSource.ANNOTATION, sourceLocation));
            resultIndex++;
        }
        return new ResultMappingDeclaration(true, List.copyOf(properties));
    }

    private ResultMappingDeclaration xmlResultMapping(
            SqlContentParser.SqlParseResult sqlInfo, String returnType) throws CompileException {
        if (sqlInfo == null || sqlInfo.resultMap() == null) {
            return ResultMappingDeclaration.none();
        }
        SqlContentParser.ResultMapInfo resultMap = sqlInfo.resultMap();
        String mappedType = extractMappedType(returnType);
        if (!resultMap.typeName().equals(mappedType)) {
            throw new CompileException(resultMap.sourceLocation() + ": declared type "
                + resultMap.typeName() + " does not match Mapper result type " + mappedType);
        }

        TypeElement mappedElement = elementUtils.getTypeElement(mappedType);
        List<? extends RecordComponentElement> recordComponents = mappedElement != null
                && mappedElement.getKind() == javax.lang.model.element.ElementKind.RECORD
            ? mappedElement.getRecordComponents() : List.of();
        List<ResultProperty> properties = new ArrayList<>(resultMap.properties().size());
        Set<String> declaredNames = new HashSet<>();
        for (SqlContentParser.ResultPropertyInfo property : resultMap.properties()) {
            String propertyName = property.property();
            if (propertyName.isBlank() && property.constructorArgument()) {
                if (property.constructorIndex() >= recordComponents.size()) {
                    throw new CompileException(property.sourceLocation()
                        + ": unnamed constructor argument has no matching record component");
                }
                propertyName = recordComponents.get(property.constructorIndex())
                    .getSimpleName().toString();
            }
            if (!declaredNames.add(propertyName)) {
                throw new CompileException(property.sourceLocation()
                    + ": duplicate result property '" + propertyName + "'");
            }
            properties.add(new ResultProperty(
                propertyName, property.column(), property.javaTypeName(),
                property.constructorArgument(), property.constructorIndex(),
                ResultMappingSource.XML, property.sourceLocation()));
        }
        return new ResultMappingDeclaration(true, List.copyOf(properties));
    }

    private void validateDeclaredJavaType(
            ResultProperty declaredProperty, TypeMirror targetType) throws CompileException {
        if (declaredProperty == null || declaredProperty.javaType() == null) {
            return;
        }
        if (!declaredProperty.javaType().equals(targetType.toString())) {
            String mappingName = declaredProperty.source() == ResultMappingSource.ANNOTATION
                ? "@Result property" : "XML result property";
            throw new CompileException(declaredProperty.sourceLocation()
                + ": " + mappingName + " '" + declaredProperty.property() + "' declares Java type "
                + declaredProperty.javaType() + " but the target type is " + targetType);
        }
    }

    private void validateDeclaredJavaType(
            ResultProperty declaredProperty, String targetType) throws CompileException {
        if (declaredProperty.javaType() == null
                || declaredProperty.javaType().equals(targetType)) {
            return;
        }
        String mappingName = declaredProperty.source() == ResultMappingSource.ANNOTATION
            ? "@Result" : "XML result mapping";
        throw new CompileException(declaredProperty.sourceLocation()
            + ": " + mappingName + " declares Java type " + declaredProperty.javaType()
            + " but the target type is " + targetType);
    }

    private void rejectUnknownResultProperties(
            String objectType, Map<String, ResultProperty> properties) throws CompileException {
        if (!properties.isEmpty()) {
            ResultProperty declaredProperty = properties.values().iterator().next();
            String mappingName = declaredProperty.source() == ResultMappingSource.ANNOTATION
                ? "@Result property" : "XML result property";
            throw new CompileException(declaredProperty.sourceLocation() + ": " + mappingName + " '"
                + declaredProperty.property() + "' does not exist on " + objectType);
        }
    }

    private CompileException unsupportedResultMapping(
            TypeElement mapperInterface, ExecutableElement method, String detail) {
        return new CompileException(mapperInterface.getQualifiedName() + "#" + method.getSimpleName()
            + ": Unsupported result mapping: " + detail + "; this result type requires @UseRowMapper");
    }

    private record ResultMapping(
            String expression,
            String helperCode,
            List<NormalizedResultProperty> properties) {

        private List<String> columnLabels() {
            return properties.stream()
                .map(NormalizedResultProperty::column)
                .filter(column -> !column.isBlank())
                .toList();
        }
    }

    private record ResultMappingDeclaration(boolean present, List<ResultProperty> properties) {

        private static ResultMappingDeclaration none() {
            return new ResultMappingDeclaration(false, List.of());
        }

        private Map<String, ResultProperty> byProperty() {
            Map<String, ResultProperty> result = new LinkedHashMap<>();
            properties.forEach(property -> result.put(property.property(), property));
            return result;
        }
    }

    private record ResultProperty(
            String property,
            String column,
            String javaType,
            boolean constructorArgument,
            int constructorIndex,
            ResultMappingSource source,
            String sourceLocation) {
    }

    private record NormalizedResultProperty(
            String property,
            String column,
            String targetJavaType,
            boolean constructorArgument,
            int constructorIndex,
            ResultMappingSource source,
            String sourceLocation) {
    }

    private enum ResultMappingSource {
        ANNOTATION,
        XML,
        CONVENTION
    }

    private record CursorMethod(
        int parameterIndex, String parameterName, TypeMirror rowType, TypeMirror resultType) {
    }
    
    /**
     * Mapper compilation failure with an optional diagnostic element.
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

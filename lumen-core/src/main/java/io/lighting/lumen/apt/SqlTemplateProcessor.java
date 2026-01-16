package io.lighting.lumen.apt;

import io.lighting.lumen.template.annotations.SqlConst;
import io.lighting.lumen.template.annotations.SqlTemplate;
import io.lighting.lumen.template.SqlTemplateAnalysis;
import io.lighting.lumen.template.SqlTemplateAnalyzer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * SQL 模板注解处理器。
 * <p>
 * 该处理器负责：
 * <ul>
 *   <li>校验 {@link SqlTemplate} 的使用位置与方法签名。</li>
 *   <li>在编译期解析 SQL 模板并检查绑定参数。</li>
 *   <li>生成模板常量类（_SqlTemplates）与实现类（_Impl）。</li>
 *   <li>校验 {@link SqlConst} 标注的常量模板。</li>
 * </ul>
 */
@SupportedAnnotationTypes({
    "io.lighting.lumen.template.annotations.SqlTemplate",
    "io.lighting.lumen.template.annotations.SqlConst"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class SqlTemplateProcessor extends AbstractProcessor {
    /**
     * 系统预留绑定名，供框架注入方言标识。
     */
    private static final String SYSTEM_DIALECT_BINDING = "__dialect";

    /**
     * 处理入口：校验模板注解并生成相关类。
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        Map<TypeElement, List<ExecutableElement>> methodsByType = new LinkedHashMap<>();
        boolean hasErrors = false;
        hasErrors |= processSqlTemplates(roundEnv, methodsByType);
        hasErrors |= processSqlConst(roundEnv);
        if (hasErrors) {
            return true;
        }
        for (Map.Entry<TypeElement, List<ExecutableElement>> entry : methodsByType.entrySet()) {
            generateTemplateOutputs(entry.getKey(), entry.getValue());
        }
        return true;
    }

    /**
     * 处理 @SqlTemplate 标注的方法。
     * <p>
     * 验证方法签名、模板语法与绑定参数，并按接口归类。
     */
    private boolean processSqlTemplates(
        RoundEnvironment roundEnv,
        Map<TypeElement, List<ExecutableElement>> methodsByType
    ) {
        boolean hasErrors = false;
        for (Element element : roundEnv.getElementsAnnotatedWith(SqlTemplate.class)) {
            if (element.getKind() != ElementKind.METHOD) {
                error(element, "@SqlTemplate can only be applied to methods");
                hasErrors = true;
                continue;
            }
            ExecutableElement method = (ExecutableElement) element;
            if (!validateEnclosingType(method)) {
                hasErrors = true;
                continue;
            }
            if (!validateMethodSignature(method)) {
                hasErrors = true;
                continue;
            }
            SqlTemplate annotation = method.getAnnotation(SqlTemplate.class);
            if (annotation == null) {
                error(method, "Missing @SqlTemplate annotation");
                hasErrors = true;
                continue;
            }
            String template = annotation.value();
            if (!validateTemplate(method, template)) {
                hasErrors = true;
                continue;
            }
            if (!validateTemplateBindings(method, template)) {
                hasErrors = true;
                continue;
            }
            TypeElement enclosing = (TypeElement) method.getEnclosingElement();
            methodsByType.computeIfAbsent(enclosing, key -> new ArrayList<>()).add(method);
        }
        return hasErrors;
    }

    /**
     * 处理 @SqlConst 标注的模板常量。
     * <p>
     * 仅允许 static final 字段或 final 局部变量，并要求为编译期常量字符串。
     */
    private boolean processSqlConst(RoundEnvironment roundEnv) {
        boolean hasErrors = false;
        for (Element element : roundEnv.getElementsAnnotatedWith(SqlConst.class)) {
            if (element.getKind() != ElementKind.FIELD && element.getKind() != ElementKind.LOCAL_VARIABLE) {
                error(element, "@SqlConst can only be applied to fields or local variables");
                hasErrors = true;
                continue;
            }
            if (element.getKind() == ElementKind.FIELD) {
                if (!element.getModifiers().containsAll(Set.of(Modifier.STATIC, Modifier.FINAL))) {
                    error(element, "@SqlConst requires a static final field");
                    hasErrors = true;
                    continue;
                }
            } else {
                if (!element.getModifiers().contains(Modifier.FINAL)) {
                    error(element, "@SqlConst requires a final local variable");
                    hasErrors = true;
                    continue;
                }
            }
            if (!element.asType().toString().equals(String.class.getName())) {
                error(element, "@SqlConst requires a String constant");
                hasErrors = true;
                continue;
            }
            VariableElement variable = (VariableElement) element;
            Object constant = variable.getConstantValue();
            if (!(constant instanceof String sql)) {
                error(element, "@SqlConst requires a compile-time constant String");
                hasErrors = true;
                continue;
            }
            if (!validateTemplate(element, sql)) {
                hasErrors = true;
            }
        }
        return hasErrors;
    }

    /**
     * 生成模板常量类与实现类。
     */
    private void generateTemplateOutputs(TypeElement type, List<ExecutableElement> methods) {
        List<TemplateMethod> templateMethods = buildTemplateMethods(methods);
        generateTemplateClass(type, templateMethods);
        generateImplClass(type, templateMethods);
    }

    /**
     * 为方法分配唯一常量名，并收集模板信息。
     */
    private List<TemplateMethod> buildTemplateMethods(List<ExecutableElement> methods) {
        List<TemplateMethod> result = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        for (ExecutableElement method : methods) {
            SqlTemplate annotation = method.getAnnotation(SqlTemplate.class);
            if (annotation == null) {
                error(method, "Missing @SqlTemplate annotation");
                continue;
            }
            String template = annotation.value();
            String baseName = AptCodegenUtils.toConstantName(method.getSimpleName().toString());
            String constantName = uniqueConstantName(baseName, usedNames);
            result.add(new TemplateMethod(method, constantName, template));
        }
        return result;
    }

    /**
     * 生成 _SqlTemplates 常量类。
     * <p>
     * 每个方法对应一个模板常量与解析后的模板对象。
     */
    private void generateTemplateClass(TypeElement type, List<TemplateMethod> methods) {
        String packageName = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
        String simpleName = type.getSimpleName().toString() + "_SqlTemplates";
        String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        StringBuilder content = new StringBuilder();
        content.append(AptCodegenUtils.packageLine(packageName));
        content.append("""
            @SuppressWarnings("unused")
            public final class %s {
                private %s() {
                }

            """.formatted(simpleName, simpleName));
        for (TemplateMethod method : methods) {
            content.append("""
                    public static final String %s = "%s";
                    public static final io.lighting.lumen.template.SqlTemplate %s_TEMPLATE =
                        io.lighting.lumen.template.SqlTemplate.parse(%s);

                """.formatted(
                method.constantName(),
                AptCodegenUtils.escapeJava(method.template()),
                method.constantName(),
                method.constantName()
            ));
        }
        content.append("}\n");
        AptCodegenUtils.writeSourceFile(
            processingEnv.getFiler(),
            processingEnv.getMessager(),
            type,
            qualifiedName,
            content.toString(),
            "Failed to generate template class: "
        );
    }

    /**
     * 生成 Mapper 的实现类（_Impl）。
     * <p>
     * 实现类会在方法内完成模板渲染与执行逻辑拼装。
     */
    private void generateImplClass(TypeElement type, List<TemplateMethod> methods) {
        String packageName = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
        String simpleName = type.getSimpleName().toString() + "_Impl";
        String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        String typeParamsDecl = renderTypeParameters(type.getTypeParameters());
        String typeParamsUse = renderTypeParameterUse(type.getTypeParameters());
        String targetType = type.getQualifiedName().toString() + typeParamsUse;
        StringBuilder content = new StringBuilder();
        content.append(AptCodegenUtils.packageLine(packageName));
        content.append("""
            @SuppressWarnings("unused")
            public final class %s%s implements %s, io.lighting.lumen.dao.DaoContextProvider {
                private final io.lighting.lumen.db.Db db;
                private final io.lighting.lumen.sql.Dialect dialect;
                private final io.lighting.lumen.meta.EntityMetaRegistry metaRegistry;
                private final io.lighting.lumen.template.EntityNameResolver entityNameResolver;
                private final io.lighting.lumen.sql.SqlRenderer renderer;
                private final io.lighting.lumen.dao.DaoContext daoContext;

                public %s(
                    io.lighting.lumen.db.Db db,
                    io.lighting.lumen.sql.Dialect dialect,
                    io.lighting.lumen.meta.EntityMetaRegistry metaRegistry,
                    io.lighting.lumen.template.EntityNameResolver entityNameResolver,
                    io.lighting.lumen.sql.SqlRenderer renderer
                ) {
                    this.db = java.util.Objects.requireNonNull(db, "db");
                    this.dialect = java.util.Objects.requireNonNull(dialect, "dialect");
                    this.metaRegistry = java.util.Objects.requireNonNull(metaRegistry, "metaRegistry");
                    this.entityNameResolver = java.util.Objects.requireNonNull(entityNameResolver, "entityNameResolver");
                    this.renderer = java.util.Objects.requireNonNull(renderer, "renderer");
                    this.daoContext = io.lighting.lumen.dao.DaoContext.of(db, renderer, metaRegistry);
                }

                public %s(
                    io.lighting.lumen.db.Db db,
                    io.lighting.lumen.sql.Dialect dialect,
                    io.lighting.lumen.meta.EntityMetaRegistry metaRegistry,
                    io.lighting.lumen.template.EntityNameResolver entityNameResolver
                ) {
                    this(
                        db,
                        dialect,
                        metaRegistry,
                        entityNameResolver,
                        new io.lighting.lumen.sql.SqlRenderer(dialect)
                    );
                }

                @Override
                public io.lighting.lumen.dao.DaoContext daoContext() {
                    return daoContext;
                }

            """.formatted(simpleName, typeParamsDecl, targetType, simpleName, simpleName));

        String templatesClass = (packageName.isEmpty() ? "" : packageName + ".")
            + type.getSimpleName() + "_SqlTemplates";
        for (TemplateMethod templateMethod : methods) {
            ExecutableElement method = templateMethod.method();
            ReturnKind kind = resolveReturnKind(method);
            VariableElement rowMapperParam = findRowMapperParam(method);
            List<VariableElement> bindingParams = bindingParameters(method, rowMapperParam);
            String methodTypeParams = renderTypeParameters(method.getTypeParameters());
            if (!methodTypeParams.isEmpty()) {
                methodTypeParams = methodTypeParams + " ";
            }
            String returnType = method.getReturnType().toString();
            String throwsClause = renderThrows(method);
            if (!throwsClause.isEmpty()) {
                throwsClause = " " + throwsClause;
            }
            content.append("""
                    @Override
                    public %s%s %s(%s)%s {
                        io.lighting.lumen.sql.Bindings bindings = %s;
                        io.lighting.lumen.template.TemplateContext context =
                            new io.lighting.lumen.template.TemplateContext(
                                bindings.asMap(),
                                dialect,
                                metaRegistry,
                                entityNameResolver
                            );
                        io.lighting.lumen.sql.RenderedSql rendered =
                            %s.%s_TEMPLATE.render(context);
            """.formatted(
                methodTypeParams,
                returnType,
                method.getSimpleName(),
                renderParameters(method),
                throwsClause,
                renderBindings(bindingParams),
                templatesClass,
                templateMethod.constantName()
            ));
            content.append(renderExecution(kind, rowMapperParam, returnType));
            content.append("    }\n\n");
        }
        content.append("}\n");
        AptCodegenUtils.writeSourceFile(
            processingEnv.getFiler(),
            processingEnv.getMessager(),
            type,
            qualifiedName,
            content.toString(),
            "Failed to generate impl class: "
        );
    }

    /**
     * 为常量名追加序号，确保在同一类内唯一。
     */
    private String uniqueConstantName(String base, Set<String> used) {
        String candidate = base;
        int index = 2;
        while (used.contains(candidate)) {
            candidate = base + "_" + index;
            index++;
        }
        used.add(candidate);
        return candidate;
    }

    /**
     * 输出编译期错误。
     */
    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    /**
     * 校验模板语法是否可解析。
     */
    private boolean validateTemplate(Element element, String template) {
        try {
            io.lighting.lumen.template.SqlTemplate.parse(template);
            return true;
        } catch (RuntimeException ex) {
            error(element, "Invalid SQL template: " + ex.getMessage());
            return false;
        }
    }

    /**
     * 校验方法必须声明在接口中。
     */
    private boolean validateEnclosingType(ExecutableElement method) {
        Element enclosing = method.getEnclosingElement();
        if (enclosing.getKind() != ElementKind.INTERFACE) {
            error(enclosing, "@SqlTemplate methods must be declared on interfaces");
            return false;
        }
        return true;
    }

    /**
     * 校验方法签名：禁止 static、必须抛 SQLException、返回类型合法、RowMapper 参数符合约束。
     */
    private boolean validateMethodSignature(ExecutableElement method) {
        if (method.getModifiers().contains(Modifier.STATIC)) {
            error(method, "@SqlTemplate methods must not be static");
            return false;
        }
        if (!declaresSqlException(method)) {
            error(method, "@SqlTemplate methods must declare throws SQLException");
            return false;
        }
        ReturnKind kind = resolveReturnKind(method);
        if (kind == ReturnKind.UNSUPPORTED) {
            error(method, "@SqlTemplate methods must return List, RenderedSql, Query, Command, int, long, or void");
            return false;
        }
        if (kind == ReturnKind.LIST && findRowMapperParam(method) == null) {
            error(method, "List-returning @SqlTemplate methods require a RowMapper parameter");
            return false;
        }
        if (kind != ReturnKind.LIST && findRowMapperParam(method) != null) {
            error(method, "RowMapper parameter is only allowed for List-returning @SqlTemplate methods");
            return false;
        }
        if (countRowMapperParams(method) > 1) {
            error(method, "@SqlTemplate methods must declare at most one RowMapper parameter");
            return false;
        }
        return true;
    }

    /**
     * 校验模板绑定是否完整，且 @orderBy 片段不包含参数。
     */
    private boolean validateTemplateBindings(ExecutableElement method, String template) {
        SqlTemplateAnalysis analysis = SqlTemplateAnalyzer.analyze(template);
        Set<String> methodBindings = new HashSet<>();
        for (VariableElement param : method.getParameters()) {
            String name = param.getSimpleName().toString();
            if (SYSTEM_DIALECT_BINDING.equals(name)) {
                error(method, "Binding name is reserved: " + name);
                return false;
            }
            if (isRowMapperParam(param)) {
                continue;
            }
            methodBindings.add(name);
        }
        Set<String> missing = new HashSet<>(analysis.bindings());
        missing.removeAll(methodBindings);
        if (!missing.isEmpty()) {
            error(method, "Missing template bindings: " + String.join(", ", missing));
            return false;
        }
        if (analysis.orderByHasParams()) {
            error(method, "@orderBy fragments must not use parameters");
            return false;
        }
        return true;
    }

    /**
     * 判断方法是否声明 throws SQLException。
     */
    private boolean declaresSqlException(ExecutableElement method) {
        TypeElement sqlException = processingEnv.getElementUtils().getTypeElement("java.sql.SQLException");
        if (sqlException == null) {
            return false;
        }
        TypeMirror sqlExceptionType = sqlException.asType();
        Types types = processingEnv.getTypeUtils();
        for (TypeMirror thrown : method.getThrownTypes()) {
            if (types.isAssignable(sqlExceptionType, thrown)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 推断方法返回类型所对应的执行分支。
     */
    private ReturnKind resolveReturnKind(ExecutableElement method) {
        TypeMirror returnType = method.getReturnType();
        Types types = processingEnv.getTypeUtils();
        if (returnType.getKind() == TypeKind.VOID) {
            return ReturnKind.VOID;
        }
        if (isSameType(returnType, "int") || isSameType(returnType, "java.lang.Integer")) {
            return ReturnKind.INT;
        }
        if (isSameType(returnType, "long") || isSameType(returnType, "java.lang.Long")) {
            return ReturnKind.LONG;
        }
        if (isSameType(returnType, "io.lighting.lumen.sql.RenderedSql")) {
            return ReturnKind.RENDERED_SQL;
        }
        if (isSameType(returnType, "io.lighting.lumen.db.Query")) {
            return ReturnKind.QUERY;
        }
        if (isSameType(returnType, "io.lighting.lumen.db.Command")) {
            return ReturnKind.COMMAND;
        }
        TypeElement listType = processingEnv.getElementUtils().getTypeElement("java.util.List");
        if (listType != null && types.isSameType(types.erasure(returnType), types.erasure(listType.asType()))) {
            return ReturnKind.LIST;
        }
        return ReturnKind.UNSUPPORTED;
    }

    /**
     * 判断类型是否与指定限定名相同（含基本类型兼容处理）。
     */
    private boolean isSameType(TypeMirror type, String qualifiedName) {
        Types types = processingEnv.getTypeUtils();
        TypeElement element = processingEnv.getElementUtils().getTypeElement(qualifiedName);
        if (element == null) {
            if ("int".equals(qualifiedName)) {
                return type.getKind() == TypeKind.INT;
            }
            if ("long".equals(qualifiedName)) {
                return type.getKind() == TypeKind.LONG;
            }
            return false;
        }
        return types.isSameType(type, element.asType());
    }

    /**
     * 查找 RowMapper 参数。
     */
    private VariableElement findRowMapperParam(ExecutableElement method) {
        for (VariableElement param : method.getParameters()) {
            if (isRowMapperParam(param)) {
                return param;
            }
        }
        return null;
    }

    /**
     * 统计 RowMapper 参数数量。
     */
    private int countRowMapperParams(ExecutableElement method) {
        int count = 0;
        for (VariableElement param : method.getParameters()) {
            if (isRowMapperParam(param)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 判断参数是否为 RowMapper（支持泛型擦除匹配）。
     */
    private boolean isRowMapperParam(VariableElement param) {
        Types types = processingEnv.getTypeUtils();
        TypeElement rowMapper = processingEnv.getElementUtils().getTypeElement("io.lighting.lumen.jdbc.RowMapper");
        if (rowMapper == null) {
            return false;
        }
        return types.isAssignable(types.erasure(param.asType()), types.erasure(rowMapper.asType()));
    }

    /**
     * 过滤掉 RowMapper 参数，得到需要绑定到模板的参数列表。
     */
    private List<VariableElement> bindingParameters(ExecutableElement method, VariableElement rowMapperParam) {
        List<VariableElement> params = new ArrayList<>();
        for (VariableElement param : method.getParameters()) {
            if (rowMapperParam != null && param.equals(rowMapperParam)) {
                continue;
            }
            params.add(param);
        }
        return params;
    }

    /**
     * 渲染方法参数列表字符串。
     */
    private String renderParameters(ExecutableElement method) {
        StringBuilder builder = new StringBuilder();
        List<? extends VariableElement> params = method.getParameters();
        for (int i = 0; i < params.size(); i++) {
            VariableElement param = params.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(param.asType().toString()).append(" ").append(param.getSimpleName());
        }
        return builder.toString();
    }

    /**
     * 渲染模板绑定表达式。
     */
    private String renderBindings(List<VariableElement> params) {
        if (params.isEmpty()) {
            return "io.lighting.lumen.sql.Bindings.empty()";
        }
        StringBuilder builder = new StringBuilder("io.lighting.lumen.sql.Bindings.of(");
        for (int i = 0; i < params.size(); i++) {
            VariableElement param = params.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append("\"").append(param.getSimpleName()).append("\", ").append(param.getSimpleName());
        }
        builder.append(")");
        return builder.toString();
    }

    /**
     * 渲染 throws 子句。
     */
    private String renderThrows(ExecutableElement method) {
        List<? extends TypeMirror> thrownTypes = method.getThrownTypes();
        if (thrownTypes.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("throws ");
        for (int i = 0; i < thrownTypes.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(thrownTypes.get(i).toString());
        }
        return builder.toString();
    }

    /**
     * 渲染执行逻辑的代码片段。
     */
    private String renderExecution(ReturnKind kind, VariableElement rowMapperParam, String returnType) {
        StringBuilder builder = new StringBuilder();
        switch (kind) {
            case LIST -> {
                String mapperName = rowMapperParam.getSimpleName().toString();
                builder.append("        return db.fetch(io.lighting.lumen.db.Query.of(rendered), ")
                    .append(mapperName).append(");\n");
            }
            case INT -> builder.append("        return db.execute(io.lighting.lumen.db.Command.of(rendered));\n");
            case LONG -> builder.append("        return (long) db.execute(io.lighting.lumen.db.Command.of(rendered));\n");
            case VOID -> builder.append("        db.execute(io.lighting.lumen.db.Command.of(rendered));\n");
            case RENDERED_SQL -> builder.append("        return rendered;\n");
            case QUERY -> builder.append("        return io.lighting.lumen.db.Query.of(rendered);\n");
            case COMMAND -> builder.append("        return io.lighting.lumen.db.Command.of(rendered);\n");
            default -> builder.append("        throw new UnsupportedOperationException(\"Unsupported return type: ")
                .append(returnType).append("\");\n");
        }
        return builder.toString();
    }

    /**
     * 渲染类型参数声明（例如 <T extends Foo>）。
     */
    private String renderTypeParameters(List<? extends TypeParameterElement> typeParams) {
        if (typeParams.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("<");
        for (int i = 0; i < typeParams.size(); i++) {
            TypeParameterElement param = typeParams.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(param.getSimpleName());
            List<? extends TypeMirror> bounds = param.getBounds();
            if (!bounds.isEmpty() && !isJavaLangObject(bounds)) {
                builder.append(" extends ");
                for (int b = 0; b < bounds.size(); b++) {
                    if (b > 0) {
                        builder.append(" & ");
                    }
                    builder.append(bounds.get(b).toString());
                }
            }
        }
        builder.append(">");
        return builder.toString();
    }

    /**
     * 渲染类型参数使用（例如 <T, R>）。
     */
    private String renderTypeParameterUse(List<? extends TypeParameterElement> typeParams) {
        if (typeParams.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("<");
        for (int i = 0; i < typeParams.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(typeParams.get(i).getSimpleName());
        }
        builder.append(">");
        return builder.toString();
    }

    /**
     * 判断泛型上界是否仅为 Object。
     */
    private boolean isJavaLangObject(List<? extends TypeMirror> bounds) {
        if (bounds.size() != 1) {
            return false;
        }
        TypeMirror bound = bounds.getFirst();
        return isSameType(bound, "java.lang.Object");
    }

    private record TemplateMethod(ExecutableElement method, String constantName, String template) {
    }

    private enum ReturnKind {
        LIST,
        INT,
        LONG,
        VOID,
        RENDERED_SQL,
        QUERY,
        COMMAND,
        UNSUPPORTED
    }
}

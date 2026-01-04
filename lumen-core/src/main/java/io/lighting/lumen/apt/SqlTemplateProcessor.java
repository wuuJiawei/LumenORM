package io.lighting.lumen.apt;

import io.lighting.lumen.template.annotations.SqlConst;
import io.lighting.lumen.template.annotations.SqlTemplate;
import io.lighting.lumen.template.SqlTemplateAnalysis;
import io.lighting.lumen.template.SqlTemplateAnalyzer;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
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
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes({
    "io.lighting.lumen.template.annotations.SqlTemplate",
    "io.lighting.lumen.template.annotations.SqlConst"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class SqlTemplateProcessor extends AbstractProcessor {
    private static final String SYSTEM_DIALECT_BINDING = "__dialect";

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

    private void generateTemplateOutputs(TypeElement type, List<ExecutableElement> methods) {
        List<TemplateMethod> templateMethods = buildTemplateMethods(methods);
        generateTemplateClass(type, templateMethods);
        generateImplClass(type, templateMethods);
    }

    private List<TemplateMethod> buildTemplateMethods(List<ExecutableElement> methods) {
        List<TemplateMethod> result = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        for (ExecutableElement method : methods) {
            SqlTemplate annotation = method.getAnnotation(SqlTemplate.class);
            String template = annotation.value();
            String baseName = toConstantName(method.getSimpleName().toString());
            String constantName = uniqueConstantName(baseName, usedNames);
            result.add(new TemplateMethod(method, constantName, template));
        }
        return result;
    }

    private void generateTemplateClass(TypeElement type, List<TemplateMethod> methods) {
        String packageName = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
        String simpleName = type.getSimpleName().toString() + "_SqlTemplates";
        String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        Filer filer = processingEnv.getFiler();
        try {
            JavaFileObject sourceFile = filer.createSourceFile(qualifiedName, type);
            try (Writer writer = sourceFile.openWriter()) {
                if (!packageName.isEmpty()) {
                    writer.write("package " + packageName + ";\n\n");
                }
                writer.write("public final class " + simpleName + " {\n");
                writer.write("    private " + simpleName + "() {\n");
                writer.write("    }\n\n");
                for (TemplateMethod method : methods) {
                    writer.write("    public static final String " + method.constantName() + " = \""
                        + escapeJava(method.template()) + "\";\n");
                    writer.write("    public static final io.lighting.lumen.template.SqlTemplate "
                        + method.constantName() + "_TEMPLATE = io.lighting.lumen.template.SqlTemplate.parse("
                        + method.constantName() + ");\n\n");
                }
                writer.write("}\n");
            }
        } catch (IOException ex) {
            processingEnv.getMessager()
                .printMessage(Diagnostic.Kind.ERROR, "Failed to generate template class: " + ex.getMessage(), type);
        }
    }

    private void generateImplClass(TypeElement type, List<TemplateMethod> methods) {
        String packageName = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
        String simpleName = type.getSimpleName().toString() + "_Impl";
        String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        String typeParamsDecl = renderTypeParameters(type.getTypeParameters());
        String typeParamsUse = renderTypeParameterUse(type.getTypeParameters());
        String targetType = type.getQualifiedName().toString() + typeParamsUse;
        Filer filer = processingEnv.getFiler();
        try {
            JavaFileObject sourceFile = filer.createSourceFile(qualifiedName, type);
            try (Writer writer = sourceFile.openWriter()) {
                if (!packageName.isEmpty()) {
                    writer.write("package " + packageName + ";\n\n");
                }
                writer.write("public final class " + simpleName + typeParamsDecl
                    + " implements " + targetType + " {\n");
                writer.write("    private final io.lighting.lumen.db.Db db;\n");
                writer.write("    private final io.lighting.lumen.sql.Dialect dialect;\n");
                writer.write("    private final io.lighting.lumen.meta.EntityMetaRegistry metaRegistry;\n");
                writer.write("    private final io.lighting.lumen.template.EntityNameResolver entityNameResolver;\n\n");
                writer.write("    public " + simpleName + "(\n");
                writer.write("        io.lighting.lumen.db.Db db,\n");
                writer.write("        io.lighting.lumen.sql.Dialect dialect,\n");
                writer.write("        io.lighting.lumen.meta.EntityMetaRegistry metaRegistry,\n");
                writer.write("        io.lighting.lumen.template.EntityNameResolver entityNameResolver\n");
                writer.write("    ) {\n");
                writer.write("        this.db = java.util.Objects.requireNonNull(db, \"db\");\n");
                writer.write("        this.dialect = java.util.Objects.requireNonNull(dialect, \"dialect\");\n");
                writer.write("        this.metaRegistry = java.util.Objects.requireNonNull(metaRegistry, \"metaRegistry\");\n");
                writer.write("        this.entityNameResolver = java.util.Objects.requireNonNull(entityNameResolver, \"entityNameResolver\");\n");
                writer.write("    }\n\n");

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
                    writer.write("    @Override\n");
                    writer.write("    public " + methodTypeParams + returnType + " "
                        + method.getSimpleName() + "(" + renderParameters(method) + ")");
                    String throwsClause = renderThrows(method);
                    if (!throwsClause.isEmpty()) {
                        writer.write(" " + throwsClause);
                    }
                    writer.write(" {\n");
                    writer.write("        io.lighting.lumen.sql.Bindings bindings = "
                        + renderBindings(bindingParams) + ";\n");
                    writer.write("        io.lighting.lumen.template.TemplateContext context = "
                        + "new io.lighting.lumen.template.TemplateContext(\n");
                    writer.write("            bindings.asMap(),\n");
                    writer.write("            dialect,\n");
                    writer.write("            metaRegistry,\n");
                    writer.write("            entityNameResolver\n");
                    writer.write("        );\n");
                    writer.write("        io.lighting.lumen.sql.RenderedSql rendered = "
                        + templatesClass + "." + templateMethod.constantName() + "_TEMPLATE.render(context);\n");
                    writer.write(renderExecution(kind, rowMapperParam, returnType));
                    writer.write("    }\n\n");
                }
                writer.write("}\n");
            }
        } catch (IOException ex) {
            processingEnv.getMessager()
                .printMessage(Diagnostic.Kind.ERROR, "Failed to generate impl class: " + ex.getMessage(), type);
        }
    }

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

    private String toConstantName(String name) {
        StringBuilder builder = new StringBuilder();
        char prev = '\0';
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (!Character.isLetterOrDigit(ch)) {
                if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '_') {
                    builder.append('_');
                }
                prev = ch;
                continue;
            }
            if (Character.isUpperCase(ch) && i > 0
                && (Character.isLowerCase(prev) || Character.isDigit(prev))) {
                builder.append('_');
            }
            builder.append(Character.toUpperCase(ch));
            prev = ch;
        }
        return builder.toString();
    }

    private String escapeJava(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '\"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (ch < 0x20 || ch > 0x7e) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }

    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private boolean validateTemplate(Element element, String template) {
        try {
            io.lighting.lumen.template.SqlTemplate.parse(template);
            return true;
        } catch (RuntimeException ex) {
            error(element, "Invalid SQL template: " + ex.getMessage());
            return false;
        }
    }

    private boolean validateEnclosingType(ExecutableElement method) {
        Element enclosing = method.getEnclosingElement();
        if (enclosing.getKind() != ElementKind.INTERFACE) {
            error(enclosing, "@SqlTemplate methods must be declared on interfaces");
            return false;
        }
        return true;
    }

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
        if (countRowMapperParams(method) > 1) {
            error(method, "@SqlTemplate methods must declare at most one RowMapper parameter");
            return false;
        }
        return true;
    }

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

    private VariableElement findRowMapperParam(ExecutableElement method) {
        for (VariableElement param : method.getParameters()) {
            if (isRowMapperParam(param)) {
                return param;
            }
        }
        return null;
    }

    private int countRowMapperParams(ExecutableElement method) {
        int count = 0;
        for (VariableElement param : method.getParameters()) {
            if (isRowMapperParam(param)) {
                count++;
            }
        }
        return count;
    }

    private boolean isRowMapperParam(VariableElement param) {
        Types types = processingEnv.getTypeUtils();
        TypeElement rowMapper = processingEnv.getElementUtils().getTypeElement("io.lighting.lumen.jdbc.RowMapper");
        if (rowMapper == null) {
            return false;
        }
        return types.isAssignable(types.erasure(param.asType()), types.erasure(rowMapper.asType()));
    }

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

    private boolean isJavaLangObject(List<? extends TypeMirror> bounds) {
        if (bounds.size() != 1) {
            return false;
        }
        TypeMirror bound = bounds.get(0);
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

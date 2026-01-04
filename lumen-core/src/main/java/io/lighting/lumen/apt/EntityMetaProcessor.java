package io.lighting.lumen.apt;

import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.Table;
import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("io.lighting.lumen.meta.Table")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class EntityMetaProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        boolean hasErrors = false;
        for (Element element : roundEnv.getElementsAnnotatedWith(Table.class)) {
            if (element.getKind() != ElementKind.CLASS && element.getKind() != ElementKind.RECORD) {
                error(element, "@Table can only be applied to classes or records");
                hasErrors = true;
                continue;
            }
            TypeElement type = (TypeElement) element;
            Table table = type.getAnnotation(Table.class);
            String tableName = table.name();
            if (tableName.isBlank()) {
                error(type, "Table name must not be blank: " + type.getQualifiedName());
                hasErrors = true;
                continue;
            }
            Map<String, String> fieldToColumn = new LinkedHashMap<>();
            Set<String> columns = new LinkedHashSet<>();
            if (!collectColumns(type, fieldToColumn, columns)) {
                hasErrors = true;
                continue;
            }
            generateQClass(type, tableName, fieldToColumn);
        }
        return hasErrors;
    }

    private boolean collectColumns(
        TypeElement type,
        Map<String, String> fieldToColumn,
        Set<String> columns
    ) {
        Types types = processingEnv.getTypeUtils();
        TypeMirror current = type.asType();
        while (current.getKind() != TypeKind.NONE) {
            TypeElement currentType = (TypeElement) types.asElement(current);
            for (Element member : currentType.getEnclosedElements()) {
                if (member.getKind() != ElementKind.FIELD) {
                    continue;
                }
                VariableElement field = (VariableElement) member;
                if (field.getModifiers().contains(Modifier.STATIC)
                    || field.getModifiers().contains(Modifier.TRANSIENT)) {
                    continue;
                }
                Column column = field.getAnnotation(Column.class);
                Id id = field.getAnnotation(Id.class);
                if (column == null && id == null) {
                    continue;
                }
                String columnName = column != null ? column.name() : field.getSimpleName().toString();
                if (columnName.isBlank()) {
                    error(field, "Column name must not be blank: " + field.getSimpleName());
                    return false;
                }
                String fieldName = field.getSimpleName().toString();
                if (fieldToColumn.containsKey(fieldName)) {
                    error(field, "Duplicate field mapping: " + fieldName);
                    return false;
                }
                if (!columns.add(columnName)) {
                    error(field, "Duplicate column mapping: " + columnName);
                    return false;
                }
                fieldToColumn.put(fieldName, columnName);
            }
            current = currentType.getSuperclass();
            if (isJavaLangObject(current)) {
                break;
            }
        }
        return true;
    }

    private boolean isJavaLangObject(TypeMirror type) {
        if (type.getKind() == TypeKind.NONE) {
            return true;
        }
        TypeElement element = (TypeElement) processingEnv.getTypeUtils().asElement(type);
        return element != null && element.getQualifiedName().contentEquals("java.lang.Object");
    }

    private void generateQClass(TypeElement type, String tableName, Map<String, String> fieldToColumn) {
        String packageName = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
        String simpleName = "Q" + type.getSimpleName();
        String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        String constName = toConstantName(type.getSimpleName().toString());
        String defaultAlias = defaultAlias(type.getSimpleName().toString());
        Filer filer = processingEnv.getFiler();
        try {
            JavaFileObject sourceFile = filer.createSourceFile(qualifiedName, type);
            try (Writer writer = sourceFile.openWriter()) {
                if (!packageName.isEmpty()) {
                    writer.write("package " + packageName + ";\n\n");
                }
                writer.write("public final class " + simpleName + " {\n");
                writer.write("    public static final " + simpleName + " " + constName + " = new "
                    + simpleName + "(\"" + escapeJava(tableName) + "\", \"" + escapeJava(defaultAlias) + "\");\n");
                writer.write("    private final String table;\n");
                writer.write("    private final String alias;\n\n");
                writer.write("    private " + simpleName + "(String table, String alias) {\n");
                writer.write("        this.table = table;\n");
                writer.write("        this.alias = alias;\n");
                writer.write("    }\n\n");
                writer.write("    public " + simpleName + " as(String alias) {\n");
                writer.write("        if (alias == null || alias.isBlank()) {\n");
                writer.write("            throw new IllegalArgumentException(\"alias must not be blank\");\n");
                writer.write("        }\n");
                writer.write("        return new " + simpleName + "(this.table, alias);\n");
                writer.write("    }\n\n");
                writer.write("    public String table() {\n");
                writer.write("        return table;\n");
                writer.write("    }\n\n");
                writer.write("    public String alias() {\n");
                writer.write("        return alias;\n");
                writer.write("    }\n\n");
                writer.write("    public io.lighting.lumen.sql.ast.TableRef ref() {\n");
                writer.write("        return new io.lighting.lumen.sql.ast.TableRef(table, alias);\n");
                writer.write("    }\n\n");
                for (Map.Entry<String, String> entry : fieldToColumn.entrySet()) {
                    String fieldName = entry.getKey();
                    String columnName = entry.getValue();
                    writer.write("    public io.lighting.lumen.dsl.ColumnRef " + fieldName + "() {\n");
                    writer.write("        return io.lighting.lumen.dsl.ColumnRef.of(alias, \""
                        + escapeJava(columnName) + "\");\n");
                    writer.write("    }\n\n");
                }
                writer.write("}\n");
            }
        } catch (IOException ex) {
            processingEnv.getMessager()
                .printMessage(Diagnostic.Kind.ERROR, "Failed to generate Q-class: " + ex.getMessage(), type);
        }
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

    private String defaultAlias(String name) {
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                return String.valueOf(Character.toLowerCase(ch));
            }
        }
        return "t";
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
}

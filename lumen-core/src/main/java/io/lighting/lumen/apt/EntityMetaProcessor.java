package io.lighting.lumen.apt;

import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.Table;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
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

/**
 * 实体元数据处理器。
 * <p>
 * 解析 {@link Table}/{@link Column}/{@link Id} 注解并生成 Q 类，
 * 便于在 DSL 中安全引用表名与列名。
 */
@SupportedAnnotationTypes("io.lighting.lumen.meta.Table")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class EntityMetaProcessor extends AbstractProcessor {

    /**
     * 处理入口：扫描 @Table 标注的类型并生成对应的 Q 类。
     * <p>
     * 若元素不符合约束，会在编译期输出错误并跳过该元素的生成。
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(Table.class)) {
            if (element.getKind() != ElementKind.CLASS && element.getKind() != ElementKind.RECORD) {
                error(element, "@Table can only be applied to classes or records");
                continue;
            }
            TypeElement type = (TypeElement) element;
            Table table = type.getAnnotation(Table.class);
            if (table == null) {
                error(type, "Missing @Table annotation: " + type.getQualifiedName());
                continue;
            }
            String tableName = table.name();
            if (tableName.isBlank()) {
                error(type, "Table name must not be blank: " + type.getQualifiedName());
                continue;
            }
            Map<String, String> fieldToColumn = new LinkedHashMap<>();
            Set<String> columns = new LinkedHashSet<>();
            if (!collectColumns(type, fieldToColumn, columns)) {
                continue;
            }
            generateQClass(type, tableName, fieldToColumn);
        }
        return true;
    }

    /**
     * 收集字段到列名的映射。
     * <p>
     * 仅收集显式标注 {@link Column} 或 {@link Id} 的字段，跳过 static/transient 字段。
     * 同时校验字段名、列名是否重复。
     */
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

    /**
     * 判断类型是否为 java.lang.Object。
     */
    private boolean isJavaLangObject(TypeMirror type) {
        if (type.getKind() == TypeKind.NONE) {
            return true;
        }
        TypeElement element = (TypeElement) processingEnv.getTypeUtils().asElement(type);
        return element != null && element.getQualifiedName().contentEquals("java.lang.Object");
    }

    /**
     * 生成 Q 类源码。
     * <p>
     * 生成内容包含：表常量、表别名、列访问方法等。
     */
    private void generateQClass(TypeElement type, String tableName, Map<String, String> fieldToColumn) {
        String packageName = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
        String simpleName = "Q" + type.getSimpleName();
        String qualifiedName = packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
        String constName = AptCodegenUtils.toConstantName(type.getSimpleName().toString());
        String defaultAlias = defaultAlias(type.getSimpleName().toString());
        StringBuilder columnsBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : fieldToColumn.entrySet()) {
            String fieldName = entry.getKey();
            String columnName = entry.getValue();
            columnsBuilder.append("""
                    public io.lighting.lumen.dsl.ColumnRef %s() {
                        return io.lighting.lumen.dsl.ColumnRef.of(alias, "%s");
                    }

                """.formatted(fieldName, AptCodegenUtils.escapeJava(columnName)));
        }
        StringBuilder content = new StringBuilder();
        content.append(AptCodegenUtils.packageLine(packageName));
        content.append("""
            @SuppressWarnings("unused")
            public final class %s {
                public static final %s %s = new %s("%s", "%s");
                private final String table;
                private final String alias;

                private %s(String table, String alias) {
                    this.table = table;
                    this.alias = alias;
                }

                public %s as(String alias) {
                    if (alias == null || alias.isBlank()) {
                        throw new IllegalArgumentException("alias must not be blank");
                    }
                    return new %s(this.table, alias);
                }

                public String table() {
                    return table;
                }

                public String alias() {
                    return alias;
                }

                public io.lighting.lumen.sql.ast.TableRef ref() {
                    return new io.lighting.lumen.sql.ast.TableRef(table, alias);
                }

            """.formatted(
            simpleName,
            simpleName,
            constName,
            simpleName,
            AptCodegenUtils.escapeJava(tableName),
            AptCodegenUtils.escapeJava(defaultAlias),
            simpleName,
            simpleName,
            simpleName
        ));
        content.append(columnsBuilder);
        content.append("}\n");
        AptCodegenUtils.writeSourceFile(
            processingEnv.getFiler(),
            processingEnv.getMessager(),
            type,
            qualifiedName,
            content.toString(),
            "Failed to generate Q-class: "
        );
    }

    /**
     * 推导默认表别名：取实体名中第一个可用的字母或数字的小写形式。
     */
    private String defaultAlias(String name) {
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                return String.valueOf(Character.toLowerCase(ch));
            }
        }
        return "t";
    }

    /**
     * 输出编译期错误。
     */
    private void error(Element element, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }
}

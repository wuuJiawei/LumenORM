package io.lighting.lumen.apt;

import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.LogicDelete;
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
 * 解析 {@link Table}/{@link Column}/{@link Id}/{@link LogicDelete} 注解并生成 UserMeta 类，
 * 便于在 DSL 中安全引用表名与列名。
 * <p>
 * 生成的 UserMeta 类包含：
 * <ul>
 *   <li>列名字符串常量（如 {@code ID = "id"}）</li>
 *   <li>DSL 列引用方法（如 {@code id()} 返回 {@code ColumnRef}）</li>
 *   <li>默认表实例（{@code TABLE}，别名 "t"）</li>
 *   <li>带别名的表实例（{@code as("alias")}）</li>
 *   <li>表信息方法（{@code tableName()}, {@code columns()}）</li>
 *   <li>逻辑删除支持（{@code deletedAt()}, {@code notDeleted()}）</li>
 * </ul>
 */
@SupportedAnnotationTypes("io.lighting.lumen.meta.Table")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public final class EntityMetaProcessor extends AbstractProcessor {

    private String logicDeleteFieldName;

    /**
     * 处理入口：扫描 @Table 标注的类型并生成对应的 UserMeta 类。
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
            logicDeleteFieldName = null;
            if (!collectColumns(type, fieldToColumn, columns)) {
                continue;
            }
            generateUserMetaClass(type, tableName, fieldToColumn);
        }
        return true;
    }

    /**
     * 收集字段到列名的映射。
     * <p>
     * 仅收集显式标注 {@link Column}、{@link Id} 或 {@link LogicDelete} 的字段，
     * 跳过 static/transient 字段。
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
                LogicDelete logicDelete = field.getAnnotation(LogicDelete.class);
                if (column == null && id == null && logicDelete == null) {
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
                
                // 记录逻辑删除字段
                if (logicDelete != null) {
                    if (logicDeleteFieldName != null) {
                        error(field, "Duplicate @LogicDelete annotation");
                        return false;
                    }
                    logicDeleteFieldName = fieldName;
                }
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
     * 生成 UserMeta 类源码。
     * <p>
     * 生成内容包含：
     * <ul>
     *   <li>列名字符串常量（如 {@code ID = "id"}）</li>
     *   <li>DSL 列引用方法（如 {@code id()} 返回 {@code ColumnRef}）</li>
     *   <li>默认表实例（{@code TABLE}）</li>
     *   <li>带别名的表实例方法（{@code as(String alias)}）</li>
     *   <li>表信息方法（{@code tableName()}, {@code columns()}）</li>
     *   <li>内部类 {@code UserMetaTable}（支持别名的表实例）</li>
     * </ul>
     */
    private void generateUserMetaClass(TypeElement type, String tableName, Map<String, String> fieldToColumn) {
        String packageName = processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
        String entityName = type.getSimpleName().toString();
        String metaClassName = entityName + "Meta";
        String metaPackageName = packageName.isEmpty() ? metaClassName : packageName + ".meta." + metaClassName;
        String constName = AptCodegenUtils.toConstantName(entityName);
        String defaultAlias = defaultAlias(entityName);
        
        // 构建列名字符串常量
        StringBuilder constantsBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : fieldToColumn.entrySet()) {
            String fieldName = entry.getKey();
            String columnName = entry.getValue();
            String constFieldName = toConstantName(fieldName);
            constantsBuilder.append(String.format("""
                public static final String %s = "%s";

                """, constFieldName, AptCodegenUtils.escapeJava(columnName)));
        }
        
        // 构建列引用方法（静态方法）
        StringBuilder staticMethodsBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : fieldToColumn.entrySet()) {
            String fieldName = entry.getKey();
            String columnName = entry.getValue();
            String methodName = toMethodName(fieldName);
            staticMethodsBuilder.append(String.format("""
                public static io.lighting.lumen.dsl.ColumnRef %s() {
                    return io.lighting.lumen.dsl.ColumnRef.of("t", "%s");
                }

                """, methodName, AptCodegenUtils.escapeJava(columnName)));
        }
        
        // 构建 UserMetaTable 内部类的列方法
        StringBuilder tableMethodsBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : fieldToColumn.entrySet()) {
            String fieldName = entry.getKey();
            String columnName = entry.getValue();
            String methodName = toMethodName(fieldName);
            tableMethodsBuilder.append(String.format("""
                public io.lighting.lumen.dsl.ColumnRef %s() {
                    return io.lighting.lumen.dsl.ColumnRef.of(alias, "%s");
                }

                """, methodName, AptCodegenUtils.escapeJava(columnName)));
        }
        
        // 添加逻辑删除支持（静态方法）
        StringBuilder staticLogicDeleteBuilder = new StringBuilder();
        if (logicDeleteFieldName != null) {
            String logicDeleteColumn = fieldToColumn.get(logicDeleteFieldName);
            String deletedAtMethodName = toMethodName(logicDeleteFieldName);
            
            staticLogicDeleteBuilder.append(String.format("""
                // ========== 逻辑删除支持 ==========
                public static io.lighting.lumen.dsl.ColumnRef deletedAt() {
                    return io.lighting.lumen.dsl.ColumnRef.of("t", "%s");
                }
                
                public static io.lighting.lumen.sql.ast.Expr notDeleted() {
                    return deletedAt().isNull();
                }
                
                """, AptCodegenUtils.escapeJava(logicDeleteColumn)));
        }
        
        // 构建列名字符串集合
        String columnsArray = fieldToColumn.values().stream()
            .map(c -> "\"" + AptCodegenUtils.escapeJava(c) + "\"")
            .collect(java.util.stream.Collectors.joining(", "));

        String tableClassName = metaClassName + "Table";
        String escapedTableName = AptCodegenUtils.escapeJava(tableName);
        String escapedDefaultAlias = AptCodegenUtils.escapeJava(defaultAlias);

        StringBuilder content = new StringBuilder();
        content.append(AptCodegenUtils.packageLine(packageName + ".meta"));
        content.append("/**\n");
        content.append(" * APT 生成的实体元数据类。\n");
        content.append(" * <p>\n");
        content.append(" * 常量形式 - 直接获取列名字符串:\n");
        content.append(" *   UserMeta.ID       → \"id\"\n");
        content.append(" *   UserMeta.NAME     → \"name\"\n");
        content.append(" *   UserMeta.STATUS   → \"status\"\n");
        content.append(" * <p>\n");
        content.append(" * 方法形式 - DSL 构建 (类型安全):\n");
        content.append(" *   UserMeta.id()     → ColumnRef(\"t\", \"id\")\n");
        content.append(" *   UserMeta.name()   → ColumnRef(\"t\", \"name\")\n");
        content.append(" *   UserMeta.status() → ColumnRef(\"t\", \"status\")\n");
        content.append(" * <p>\n");
        content.append(" * 表实例:\n");
        content.append(" *   UserMeta.TABLE                → 别名 \"t\" 的表实例\n");
        content.append(" *   UserMeta.TABLE.as(\"u\")        → 别名 \"u\" 的表实例\n");
        content.append(" * <p>\n");
        content.append(" * 表信息:\n");
        content.append(" *   UserMeta.tableName()  → 表名字符串\n");
        content.append(" *   UserMeta.columns()    → 所有列名的集合\n");
        content.append(" * <p>\n");
        content.append(" * 逻辑删除（如果配置了 @LogicDelete）:\n");
        content.append(" *   UserMeta.TABLE.deletedAt()  → 逻辑删除列引用\n");
        content.append(" *   UserMeta.TABLE.notDeleted() → 未删除条件\n");
        content.append(" */\n");
        content.append("@SuppressWarnings(\"unused\")\n");
        content.append("public final class ").append(metaClassName).append(" {\n");
        content.append("    // ========== 常量形式：列名字符串 ==========\n");
        content.append(constantsBuilder.toString().trim()).append("\n");
        content.append("    // ========== 方法形式：DSL 列引用 ==========\n");
        content.append(staticMethodsBuilder.toString().trim()).append("\n");
        content.append("    // ========== 表实例 ==========\n");
        content.append("    public static final ").append(tableClassName).append(" TABLE = new ");
        content.append(tableClassName).append("(\"").append(escapedTableName).append("\", \"").append(escapedDefaultAlias).append("\");\n\n");
        content.append("    public ").append(tableClassName).append(" as(String alias) {\n");
        content.append("        return new ").append(tableClassName).append("(\"").append(escapedTableName).append("\", alias);\n");
        content.append("    }\n\n");
        content.append("    // ========== 表信息 ==========\n");
        content.append("    public static String tableName() {\n");
        content.append("        return \"").append(escapedTableName).append("\";\n");
        content.append("    }\n\n");
        content.append("    public static java.util.Set<String> columns() {\n");
        content.append("        return java.util.Set.of(").append(columnsArray).append(");\n");
        content.append("    }\n\n");
        content.append("    // ========== 逻辑删除支持 ==========\n");
        content.append(staticLogicDeleteBuilder.toString().trim()).append("\n");
        content.append("}\n\n");
        content.append("/**\n");
        content.append(" * 表实例（支持别名）。\n");
        content.append(" * <p>\n");
        content.append(" * 使用示例:\n");
        content.append(" *   var u = UserMeta.TABLE.as(\"u\");\n");
        content.append(" *   dsl.select(u.id(), u.name()).from(u).where(u.name().eq(\"John\"))\n");
        content.append(" * <p>\n");
        content.append(" * 逻辑删除示例:\n");
        content.append(" *   var t = UserMeta.TABLE;\n");
        content.append(" *   dsl.selectFrom(t).where(t.notDeleted())\n");
        content.append(" */\n");
        content.append("final class ").append(tableClassName).append(" {\n");
        content.append("    private final String table;\n");
        content.append("    private final String alias;\n\n");
        content.append("    ").append(tableClassName).append("(String table, String alias) {\n");
        content.append("        this.table = table;\n");
        content.append("        this.alias = alias;\n");
        content.append("    }\n\n");
        content.append("    public String table() {\n");
        content.append("        return table;\n");
        content.append("    }\n\n");
        content.append("    public String alias() {\n");
        content.append("        return alias;\n");
        content.append("    }\n\n");
        content.append(tableMethodsBuilder.toString().trim()).append("\n");
        content.append("}\n");
        
        AptCodegenUtils.writeSourceFile(
            processingEnv.getFiler(),
            processingEnv.getMessager(),
            type,
            metaPackageName,
            content.toString(),
            "Failed to generate UserMeta class: "
        );
    }

    /**
     * 将字段名转换为常量名（用于列名字符串常量）。
     * 例如：createdAt → CREATED_AT
     */
    private String toConstantName(String fieldName) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append('_').append(c);
            } else {
                result.append(Character.toUpperCase(c));
            }
        }
        return result.toString();
    }

    /**
     * 将字段名转换为方法名。
     * 例如：createdAt → createdAt, status → status
     */
    private String toMethodName(String fieldName) {
        if (fieldName.isEmpty()) {
            return fieldName;
        }
        // 首字母小写
        return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
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

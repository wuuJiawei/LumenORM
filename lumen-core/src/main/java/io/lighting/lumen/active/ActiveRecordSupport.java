package io.lighting.lumen.active;

import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.db.Query;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.dsl.PropertyNames;
import io.lighting.lumen.dsl.PropertyRef;
import io.lighting.lumen.dsl.Table;
import io.lighting.lumen.meta.EntityMeta;
import io.lighting.lumen.meta.IdMeta;
import io.lighting.lumen.meta.IdStrategy;
import io.lighting.lumen.meta.LogicDeleteMeta;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.SelectStmt;
import io.lighting.lumen.sql.ast.UpdateItem;
import io.lighting.lumen.sql.ast.UpdateStmt;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public abstract class ActiveRecordSupport<T extends ActiveRecordSupport<T>> {
    private static final AtomicReference<ActiveRecordConfig> CONFIG = new AtomicReference<>();
    private static final ConcurrentMap<Class<?>, Map<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    public static void configure(ActiveRecordConfig config) {
        CONFIG.set(Objects.requireNonNull(config, "config"));
    }

    protected static ActiveRecordConfig requireConfig() {
        ActiveRecordConfig config = CONFIG.get();
        if (config == null) {
            throw new IllegalStateException("ActiveRecordConfig is not configured");
        }
        return config;
    }

    protected Class<?> entityType() {
        return getClass();
    }

    protected void beforeInsert() {
    }

    protected void afterInsert(int rows) {
    }

    protected void beforeUpdate() {
    }

    protected void afterUpdate(int rows) {
    }

    protected void beforeDelete(boolean logical) {
    }

    protected void afterDelete(int rows, boolean logical) {
    }

    protected void beforeSave() {
    }

    protected void afterSave(int rows) {
    }

    protected int insertRows() throws SQLException {
        beforeInsert();
        ActiveRecordConfig config = requireConfig();
        EntityMeta meta = meta(config);
        IdMeta idMeta = meta.idMeta().orElse(null);
        LogicDeleteMeta logicDeleteMeta = meta.logicDeleteMeta().orElse(null);
        Object idValue = idMeta != null ? readFieldValue(idMeta.fieldName()) : null;
        boolean hasIdValue = idMeta != null && hasIdValue(idMeta, idValue);

        if (idMeta != null && idMeta.strategy() != IdStrategy.AUTO && !hasIdValue) {
            Object generated = config.idGenerator().generate(entityType()).orElse(null);
            if (generated != null) {
                setFieldValue(idMeta.fieldName(), coerceValue(generated, fieldType(idMeta.fieldName())));
                idValue = readFieldValue(idMeta.fieldName());
                hasIdValue = true;
            }
        }

        if (logicDeleteMeta != null) {
            Object current = readFieldValue(logicDeleteMeta.fieldName());
            if (current == null) {
                setFieldValue(logicDeleteMeta.fieldName(), logicDeleteMeta.activeValue());
            }
        }

        Dsl dsl = config.dsl();
        Table table = dsl.table(entityType());
        List<io.lighting.lumen.dsl.ColumnRef> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, String> entry : meta.fieldToColumn().entrySet()) {
            String fieldName = entry.getKey();
            if (idMeta != null && fieldName.equals(idMeta.fieldName()) && idMeta.strategy() == IdStrategy.AUTO && !hasIdValue) {
                continue;
            }
            Object value = readFieldValue(fieldName);
            if (logicDeleteMeta != null && fieldName.equals(logicDeleteMeta.fieldName()) && value == null) {
                value = logicDeleteMeta.activeValue();
            }
            columns.add(table.col(fieldName));
            values.add(value);
        }

        RenderedSql rendered = config.renderer().render(
            dsl.insertInto(table)
                .columns(columns.toArray(new io.lighting.lumen.dsl.ColumnRef[0]))
                .row(values.toArray())
                .build(),
            Bindings.empty()
        );
        int rows;
        if (idMeta != null && idMeta.strategy() == IdStrategy.AUTO && !hasIdValue) {
            Object generated = config.db().executeAndReturnGeneratedKey(
                Command.of(rendered),
                idMeta.columnName(),
                rs -> rs.getObject(1)
            );
            if (generated != null) {
                setFieldValue(idMeta.fieldName(), coerceValue(generated, fieldType(idMeta.fieldName())));
            }
            rows = 1;
        } else {
            rows = config.db().execute(Command.of(rendered));
        }
        afterInsert(rows);
        return rows;
    }

    protected int updateRows() throws SQLException {
        beforeUpdate();
        ActiveRecordConfig config = requireConfig();
        EntityMeta meta = meta(config);
        IdMeta idMeta = requireId(meta);
        LogicDeleteMeta logicDeleteMeta = meta.logicDeleteMeta().orElse(null);

        Object idValue = requireIdValue(idMeta);
        Dsl dsl = config.dsl();
        Table table = dsl.table(entityType());
        List<UpdateItem> assignments = new ArrayList<>();
        for (Map.Entry<String, String> entry : meta.fieldToColumn().entrySet()) {
            String fieldName = entry.getKey();
            if (fieldName.equals(idMeta.fieldName())) {
                continue;
            }
            if (logicDeleteMeta != null && fieldName.equals(logicDeleteMeta.fieldName())) {
                continue;
            }
            Object value = readFieldValue(fieldName);
            assignments.add(new UpdateItem(table.col(fieldName).expr(), new Expr.Literal(value)));
        }
        if (assignments.isEmpty()) {
            throw new IllegalStateException("No updatable fields on " + entityType().getName());
        }
        Expr where = table.col(idMeta.fieldName()).eq(idValue);
        if (logicDeleteMeta != null && config.filterLogicalDelete()) {
            where = and(where, table.notDeleted());
        }
        UpdateStmt stmt = new UpdateStmt(table.ref(), assignments, where);
        RenderedSql rendered = config.renderer().render(stmt, Bindings.empty());
        int rows = config.db().execute(Command.of(rendered));
        afterUpdate(rows);
        return rows;
    }

    protected int deleteRows() throws SQLException {
        ActiveRecordConfig config = requireConfig();
        EntityMeta meta = meta(config);
        LogicDeleteMeta logicDeleteMeta = meta.logicDeleteMeta().orElse(null);
        if (logicDeleteMeta != null) {
            return logicalDeleteRows();
        }
        return hardDeleteRows();
    }

    protected int hardDeleteRows() throws SQLException {
        beforeDelete(false);
        ActiveRecordConfig config = requireConfig();
        EntityMeta meta = meta(config);
        IdMeta idMeta = requireId(meta);
        Object idValue = requireIdValue(idMeta);
        Dsl dsl = config.dsl();
        Table table = dsl.table(entityType());
        RenderedSql rendered = config.renderer().render(
            dsl.deleteFrom(table).where(table.col(idMeta.fieldName()).eq(idValue)).build(),
            Bindings.empty()
        );
        int rows = config.db().execute(Command.of(rendered));
        afterDelete(rows, false);
        return rows;
    }

    protected int logicalDeleteRows() throws SQLException {
        beforeDelete(true);
        ActiveRecordConfig config = requireConfig();
        EntityMeta meta = meta(config);
        IdMeta idMeta = requireId(meta);
        Object idValue = requireIdValue(idMeta);
        Dsl dsl = config.dsl();
        Table table = dsl.table(entityType());
        Expr where = table.col(idMeta.fieldName()).eq(idValue);
        if (config.filterLogicalDelete()) {
            where = and(where, table.notDeleted());
        }
        RenderedSql rendered = config.renderer().render(
            dsl.logicalDeleteFrom(table).where(where).build(),
            Bindings.empty()
        );
        int rows = config.db().execute(Command.of(rendered));
        afterDelete(rows, true);
        return rows;
    }

    protected int saveRows() throws SQLException {
        beforeSave();
        ActiveRecordConfig config = requireConfig();
        EntityMeta meta = meta(config);
        IdMeta idMeta = meta.idMeta().orElse(null);
        boolean hasId = idMeta != null && hasIdValue(idMeta, readFieldValue(idMeta.fieldName()));
        int rows = hasId ? updateRows() : insertRows();
        afterSave(rows);
        return rows;
    }

    public static <E> E findById(Class<E> type, Object id) throws SQLException {
        return list(type, idCondition(type, id)).stream().findFirst().orElse(null);
    }

    public static <E> List<E> list(Class<E> type) throws SQLException {
        return list(type, null);
    }

    public static <E> List<E> list(Class<E> type, Expr where) throws SQLException {
        ActiveRecordConfig config = requireConfig();
        EntityMeta meta = config.metaRegistry().metaOf(type);
        Dsl dsl = config.dsl();
        Table table = dsl.table(type);
        List<io.lighting.lumen.dsl.ColumnRef> items = new ArrayList<>();
        for (String fieldName : meta.fieldToColumn().keySet()) {
            items.add(table.col(fieldName));
        }
        Expr finalWhere = where;
        if (config.filterLogicalDelete() && meta.logicDeleteMeta().isPresent()) {
            finalWhere = finalWhere == null ? table.notDeleted() : and(finalWhere, table.notDeleted());
        }
        SelectStmt stmt = buildSelect(dsl, table, items, finalWhere);
        Db db = config.db();
        return db.fetch(Query.of(stmt, Bindings.empty()), type);
    }

    public <R> List<R> hasMany(Class<R> targetType, String foreignKeyField) throws SQLException {
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(foreignKeyField, "foreignKeyField");
        Object id = requireIdValue(requireId(meta(requireConfig())));
        return list(targetType, configDsl().table(targetType).col(foreignKeyField).eq(id));
    }

    public <R> List<R> hasMany(Class<R> targetType, PropertyRef<R, ?> foreignKey) throws SQLException {
        Objects.requireNonNull(foreignKey, "foreignKey");
        return hasMany(targetType, PropertyNames.name(foreignKey));
    }

    public <R> R belongsTo(Class<R> targetType, String localField, String targetField) throws SQLException {
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(localField, "localField");
        Objects.requireNonNull(targetField, "targetField");
        Object value = readFieldValue(localField);
        if (value == null) {
            return null;
        }
        Expr where = configDsl().table(targetType).col(targetField).eq(value);
        return list(targetType, where).stream().findFirst().orElse(null);
    }

    public <R> R belongsTo(Class<R> targetType, PropertyRef<T, ?> localKey, PropertyRef<R, ?> targetKey)
        throws SQLException {
        Objects.requireNonNull(localKey, "localKey");
        Objects.requireNonNull(targetKey, "targetKey");
        return belongsTo(targetType, PropertyNames.name(localKey), PropertyNames.name(targetKey));
    }

    public <R> R hasOne(Class<R> targetType, String foreignKeyField) throws SQLException {
        return hasMany(targetType, foreignKeyField).stream().findFirst().orElse(null);
    }

    public <R> R hasOne(Class<R> targetType, PropertyRef<R, ?> foreignKey) throws SQLException {
        return hasMany(targetType, foreignKey).stream().findFirst().orElse(null);
    }

    protected static Expr and(Expr left, Expr right) {
        return new Expr.And(List.of(left, right));
    }

    protected static Expr idCondition(Class<?> type, Object id) {
        ActiveRecordConfig config = requireConfig();
        EntityMeta meta = config.metaRegistry().metaOf(type);
        IdMeta idMeta = meta.idMeta()
            .orElseThrow(() -> new IllegalArgumentException("Missing @Id on " + type.getName()));
        return config.dsl().table(type).col(idMeta.fieldName()).eq(id);
    }

    protected EntityMeta meta(ActiveRecordConfig config) {
        return config.metaRegistry().metaOf(entityType());
    }

    protected Dsl configDsl() {
        return requireConfig().dsl();
    }

    protected static IdMeta requireId(EntityMeta meta) {
        return meta.idMeta()
            .orElseThrow(() -> new IllegalArgumentException("Missing @Id on " + meta.table()));
    }

    protected Object requireIdValue(IdMeta idMeta) {
        Object value = readFieldValue(idMeta.fieldName());
        if (!hasIdValue(idMeta, value)) {
            throw new IllegalStateException("Missing id value for " + entityType().getName());
        }
        return value;
    }

    protected boolean hasIdValue(IdMeta idMeta, Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Number number) {
            return number.longValue() != 0L || idMeta.strategy() != IdStrategy.AUTO;
        }
        if (value instanceof String text) {
            return !text.isBlank();
        }
        return true;
    }

    private Field fieldFor(String fieldName) {
        Map<String, Field> fields = FIELD_CACHE.computeIfAbsent(entityType(), ActiveRecordSupport::scanFields);
        Field field = fields.get(fieldName);
        if (field == null) {
            throw new IllegalArgumentException("Unknown field: " + fieldName);
        }
        return field;
    }

    protected Object readFieldValue(String fieldName) {
        try {
            return fieldFor(fieldName).get(this);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Failed to read field " + fieldName, ex);
        }
    }

    protected void setFieldValue(String fieldName, Object value) {
        try {
            fieldFor(fieldName).set(this, value);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Failed to set field " + fieldName, ex);
        }
    }

    private Class<?> fieldType(String fieldName) {
        return fieldFor(fieldName).getType();
    }

    private Object coerceValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType == String.class) {
            return value.toString();
        }
        if (value instanceof Number number) {
            if (targetType == long.class || targetType == Long.class) {
                return number.longValue();
            }
            if (targetType == int.class || targetType == Integer.class) {
                return number.intValue();
            }
            if (targetType == short.class || targetType == Short.class) {
                return number.shortValue();
            }
            if (targetType == byte.class || targetType == Byte.class) {
                return number.byteValue();
            }
        }
        return value;
    }

    private static Map<String, Field> scanFields(Class<?> type) {
        Map<String, Field> fields = new ConcurrentHashMap<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                field.setAccessible(true);
                fields.putIfAbsent(field.getName(), field);
            }
        }
        return fields;
    }

    private static SelectStmt buildSelect(
        Dsl dsl,
        Table table,
        List<io.lighting.lumen.dsl.ColumnRef> columns,
        Expr where
    ) {
        var builder = dsl.select(columns.stream().map(io.lighting.lumen.dsl.ColumnRef::select).toArray(io.lighting.lumen.sql.ast.SelectItem[]::new))
            .from(table);
        if (where != null) {
            builder.where(where);
        }
        return builder.build();
    }
}

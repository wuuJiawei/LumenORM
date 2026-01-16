package io.lighting.lumen.dao;

import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.db.Query;
import io.lighting.lumen.dsl.ColumnRef;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.dsl.Table;
import io.lighting.lumen.jdbc.RowMappers;
import io.lighting.lumen.meta.EntityMeta;
import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.meta.IdMeta;
import io.lighting.lumen.meta.IdStrategy;
import io.lighting.lumen.meta.LogicDeleteMeta;
import io.lighting.lumen.page.PageRequest;
import io.lighting.lumen.page.PageResult;
import io.lighting.lumen.page.Sort;
import io.lighting.lumen.page.SortDirection;
import io.lighting.lumen.page.SortItem;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.SqlRenderer;
import io.lighting.lumen.sql.Dialect;
import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.OrderItem;
import io.lighting.lumen.sql.ast.Paging;
import io.lighting.lumen.sql.ast.SelectItem;
import io.lighting.lumen.sql.ast.SelectStmt;
import io.lighting.lumen.sql.ast.UpdateItem;
import io.lighting.lumen.sql.ast.UpdateStmt;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class DaoSupport {
    private static final ConcurrentMap<Class<?>, Class<?>> ENTITY_TYPE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Map<String, Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    private DaoSupport() {
    }

    static DaoContext context(Object dao) {
        Objects.requireNonNull(dao, "dao");
        if (dao instanceof DaoContextProvider provider) {
            return provider.daoContext();
        }
        if (Proxy.isProxyClass(dao.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(dao);
            if (handler instanceof DaoContextProvider provider) {
                return provider.daoContext();
            }
        }
        DaoContext context = findContextField(dao);
        if (context != null) {
            return context;
        }
        Db db = requireField(dao, Db.class, "db");
        EntityMetaRegistry metaRegistry = requireField(dao, EntityMetaRegistry.class, "metaRegistry");
        SqlRenderer renderer = findField(dao, SqlRenderer.class, "renderer");
        if (renderer == null) {
            Dialect dialect = requireField(dao, Dialect.class, "dialect");
            renderer = new SqlRenderer(dialect);
        }
        return DaoContext.of(db, renderer, metaRegistry);
    }

    static Class<?> entityType(Object dao) {
        Objects.requireNonNull(dao, "dao");
        Class<?> daoClass = dao instanceof Class<?> type ? type : dao.getClass();
        return ENTITY_TYPE_CACHE.computeIfAbsent(daoClass, DaoSupport::resolveEntityType);
    }

    static <T> int insert(DaoContext context, Class<T> entityType, T entity) throws SQLException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entity, "entity");
        EntityMeta meta = context.metaRegistry().metaOf(entityType);
        IdMeta idMeta = meta.idMeta().orElse(null);
        LogicDeleteMeta logicDeleteMeta = meta.logicDeleteMeta().orElse(null);
        Object idValue = idMeta != null ? readFieldValue(entity, entityType, idMeta.fieldName()) : null;
        boolean hasIdValue = idMeta != null && hasIdValue(idMeta, idValue);

        if (idMeta != null && idMeta.strategy() != IdStrategy.AUTO && !hasIdValue) {
            Object generated = context.idGenerator().generate(entityType).orElse(null);
            if (generated != null) {
                setFieldValue(entity, entityType, idMeta.fieldName(), coerceValue(generated, fieldType(entityType, idMeta.fieldName())));
                idValue = readFieldValue(entity, entityType, idMeta.fieldName());
                hasIdValue = true;
            }
        }

        if (logicDeleteMeta != null) {
            Object current = readFieldValue(entity, entityType, logicDeleteMeta.fieldName());
            if (current == null) {
                setFieldValue(entity, entityType, logicDeleteMeta.fieldName(), logicDeleteMeta.activeValue());
            }
        }

        Dsl dsl = context.dsl();
        Table table = dsl.table(entityType);
        List<ColumnRef> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        for (Map.Entry<String, String> entry : meta.fieldToColumn().entrySet()) {
            String fieldName = entry.getKey();
            if (idMeta != null && fieldName.equals(idMeta.fieldName()) && idMeta.strategy() == IdStrategy.AUTO && !hasIdValue) {
                continue;
            }
            Object value = readFieldValue(entity, entityType, fieldName);
            if (logicDeleteMeta != null && fieldName.equals(logicDeleteMeta.fieldName()) && value == null) {
                value = logicDeleteMeta.activeValue();
            }
            columns.add(table.col(fieldName));
            values.add(value);
        }

        RenderedSql rendered = context.renderer().render(
            dsl.insertInto(table)
                .columns(columns.toArray(new ColumnRef[0]))
                .row(values.toArray())
                .build(),
            Bindings.empty()
        );
        if (idMeta != null && idMeta.strategy() == IdStrategy.AUTO && !hasIdValue) {
            Object generated = context.db().executeAndReturnGeneratedKey(
                Command.of(rendered),
                idMeta.columnName(),
                rs -> rs.getObject(1)
            );
            if (generated != null) {
                setFieldValue(entity, entityType, idMeta.fieldName(), coerceValue(generated, fieldType(entityType, idMeta.fieldName())));
            }
            return 1;
        }
        return context.db().execute(Command.of(rendered));
    }

    static <T> int updateById(DaoContext context, Class<T> entityType, T entity) throws SQLException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entity, "entity");
        EntityMeta meta = context.metaRegistry().metaOf(entityType);
        IdMeta idMeta = requireId(meta);
        LogicDeleteMeta logicDeleteMeta = meta.logicDeleteMeta().orElse(null);
        Object idValue = requireIdValue(entity, entityType, idMeta);
        Dsl dsl = context.dsl();
        Table table = dsl.table(entityType);
        List<UpdateItem> assignments = new ArrayList<>();
        for (Map.Entry<String, String> entry : meta.fieldToColumn().entrySet()) {
            String fieldName = entry.getKey();
            if (fieldName.equals(idMeta.fieldName())) {
                continue;
            }
            if (logicDeleteMeta != null && fieldName.equals(logicDeleteMeta.fieldName())) {
                continue;
            }
            Object value = readFieldValue(entity, entityType, fieldName);
            assignments.add(new UpdateItem(table.col(fieldName).expr(), new Expr.Literal(value)));
        }
        if (assignments.isEmpty()) {
            throw new IllegalStateException("No updatable fields on " + entityType.getName());
        }
        Expr where = table.col(idMeta.fieldName()).eq(idValue);
        if (logicDeleteMeta != null && context.filterLogicalDelete()) {
            where = and(where, table.notDeleted());
        }
        UpdateStmt stmt = new UpdateStmt(table.ref(), assignments, where);
        RenderedSql rendered = context.renderer().render(stmt, Bindings.empty());
        return context.db().execute(Command.of(rendered));
    }

    static int deleteById(DaoContext context, Class<?> entityType, Object id) throws SQLException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(entityType, "entityType");
        EntityMeta meta = context.metaRegistry().metaOf(entityType);
        IdMeta idMeta = requireId(meta);
        if (!hasIdValue(idMeta, id)) {
            throw new IllegalArgumentException("Missing id value for " + entityType.getName());
        }
        LogicDeleteMeta logicDeleteMeta = meta.logicDeleteMeta().orElse(null);
        if (logicDeleteMeta != null) {
            return logicalDeleteById(context, entityType, idMeta, id);
        }
        return hardDeleteById(context, entityType, idMeta, id);
    }

    static <T> T selectById(DaoContext context, Class<T> entityType, Object id) throws SQLException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(entityType, "entityType");
        EntityMeta meta = context.metaRegistry().metaOf(entityType);
        IdMeta idMeta = requireId(meta);
        if (!hasIdValue(idMeta, id)) {
            throw new IllegalArgumentException("Missing id value for " + entityType.getName());
        }
        Dsl dsl = context.dsl();
        Table table = dsl.table(entityType);
        List<ColumnRef> columns = columnsFor(meta, table);
        Expr where = table.col(idMeta.fieldName()).eq(id);
        where = applyLogicalDeleteFilter(context, meta, table, where);
        SelectStmt stmt = buildSelect(dsl, table, columns, where, List.of(), null);
        List<T> rows = context.db().fetch(Query.of(stmt, Bindings.empty()), RowMappers.auto(entityType));
        return rows.isEmpty() ? null : rows.get(0);
    }

    static <T> List<T> selectList(DaoContext context, Class<T> entityType, Expr where) throws SQLException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(entityType, "entityType");
        EntityMeta meta = context.metaRegistry().metaOf(entityType);
        Dsl dsl = context.dsl();
        Table table = dsl.table(entityType);
        List<ColumnRef> columns = columnsFor(meta, table);
        Expr finalWhere = applyLogicalDeleteFilter(context, meta, table, where);
        SelectStmt stmt = buildSelect(dsl, table, columns, finalWhere, List.of(), null);
        return context.db().fetch(Query.of(stmt, Bindings.empty()), RowMappers.auto(entityType));
    }

    static <T> PageResult<T> selectPage(
        DaoContext context,
        Class<T> entityType,
        PageRequest pageRequest,
        Expr where
    ) throws SQLException {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(pageRequest, "pageRequest");
        EntityMeta meta = context.metaRegistry().metaOf(entityType);
        Dsl dsl = context.dsl();
        Table table = dsl.table(entityType);
        List<ColumnRef> columns = columnsFor(meta, table);
        Expr finalWhere = applyLogicalDeleteFilter(context, meta, table, where);
        List<OrderItem> orderItems = resolveSort(pageRequest.sort(), table, meta);
        SelectStmt pageStmt = buildSelect(
            dsl,
            table,
            columns,
            finalWhere,
            orderItems,
            new Paging(pageRequest.page(), pageRequest.pageSize())
        );
        SelectStmt countStmt = buildSelect(dsl, table, columns, finalWhere, List.of(), null);
        Query pageQuery = Query.of(pageStmt, Bindings.empty());
        Query countQuery = pageRequest.searchCount() ? Query.count(Query.of(countStmt, Bindings.empty())) : null;
        return context.db().page(pageQuery, countQuery, pageRequest, RowMappers.auto(entityType));
    }

    private static int hardDeleteById(
        DaoContext context,
        Class<?> entityType,
        IdMeta idMeta,
        Object id
    ) throws SQLException {
        Dsl dsl = context.dsl();
        Table table = dsl.table(entityType);
        RenderedSql rendered = context.renderer().render(
            dsl.deleteFrom(table).where(table.col(idMeta.fieldName()).eq(id)).build(),
            Bindings.empty()
        );
        return context.db().execute(Command.of(rendered));
    }

    private static int logicalDeleteById(
        DaoContext context,
        Class<?> entityType,
        IdMeta idMeta,
        Object id
    ) throws SQLException {
        Dsl dsl = context.dsl();
        Table table = dsl.table(entityType);
        Expr where = table.col(idMeta.fieldName()).eq(id);
        if (context.filterLogicalDelete()) {
            where = and(where, table.notDeleted());
        }
        RenderedSql rendered = context.renderer().render(
            dsl.logicalDeleteFrom(table).where(where).build(),
            Bindings.empty()
        );
        return context.db().execute(Command.of(rendered));
    }

    private static Expr applyLogicalDeleteFilter(
        DaoContext context,
        EntityMeta meta,
        Table table,
        Expr where
    ) {
        if (!context.filterLogicalDelete() || meta.logicDeleteMeta().isEmpty()) {
            return where;
        }
        Expr notDeleted = table.notDeleted();
        if (where == null) {
            return notDeleted;
        }
        return and(where, notDeleted);
    }

    private static Expr and(Expr left, Expr right) {
        return new Expr.And(List.of(left, right));
    }

    private static List<ColumnRef> columnsFor(EntityMeta meta, Table table) {
        List<ColumnRef> columns = new ArrayList<>(meta.fieldToColumn().size());
        for (String fieldName : meta.fieldToColumn().keySet()) {
            columns.add(table.col(fieldName));
        }
        return columns;
    }

    private static SelectStmt buildSelect(
        Dsl dsl,
        Table table,
        List<ColumnRef> columns,
        Expr where,
        List<OrderItem> orderBy,
        Paging paging
    ) {
        SelectItem[] items = columns.stream().map(ColumnRef::select).toArray(SelectItem[]::new);
        var builder = dsl.select(items).from(table);
        if (where != null) {
            builder.where(where);
        }
        if (orderBy != null && !orderBy.isEmpty()) {
            builder.orderBy(orderBy.toArray(new OrderItem[0]));
        }
        if (paging != null) {
            builder.page(paging.page(), paging.pageSize());
        }
        return builder.build();
    }

    private static List<OrderItem> resolveSort(Sort sort, Table table, EntityMeta meta) {
        if (sort == null || sort.isEmpty()) {
            return List.of();
        }
        List<OrderItem> items = new ArrayList<>(sort.items().size());
        for (SortItem item : sort.items()) {
            String columnName = resolveColumnName(meta, item.key());
            ColumnRef column = ColumnRef.of(table.alias(), columnName);
            boolean asc = item.direction() == SortDirection.ASC;
            items.add(new OrderItem(column.expr(), asc));
        }
        return List.copyOf(items);
    }

    private static String resolveColumnName(EntityMeta meta, String key) {
        String column = meta.fieldToColumn().get(key);
        if (column != null) {
            return column;
        }
        if (meta.columns().contains(key)) {
            return key;
        }
        throw new IllegalArgumentException("Unknown sort key: " + key);
    }

    private static IdMeta requireId(EntityMeta meta) {
        return meta.idMeta()
            .orElseThrow(() -> new IllegalArgumentException("Missing @Id on " + meta.table()));
    }

    private static Object requireIdValue(Object entity, Class<?> entityType, IdMeta idMeta) {
        Object value = readFieldValue(entity, entityType, idMeta.fieldName());
        if (!hasIdValue(idMeta, value)) {
            throw new IllegalStateException("Missing id value for " + entityType.getName());
        }
        return value;
    }

    private static boolean hasIdValue(IdMeta idMeta, Object value) {
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

    private static Object readFieldValue(Object entity, Class<?> entityType, String fieldName) {
        Field field = fieldFor(entityType, fieldName);
        try {
            return field.get(entity);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Failed to read field " + fieldName, ex);
        }
    }

    private static void setFieldValue(Object entity, Class<?> entityType, String fieldName, Object value) {
        Field field = fieldFor(entityType, fieldName);
        try {
            field.set(entity, value);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Failed to set field " + fieldName, ex);
        }
    }

    private static Class<?> fieldType(Class<?> entityType, String fieldName) {
        return fieldFor(entityType, fieldName).getType();
    }

    private static Object coerceValue(Object value, Class<?> targetType) {
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

    private static Field fieldFor(Class<?> entityType, String fieldName) {
        Map<String, Field> fields = FIELD_CACHE.computeIfAbsent(entityType, DaoSupport::scanFields);
        Field field = fields.get(fieldName);
        if (field == null) {
            throw new IllegalArgumentException("Unknown field: " + fieldName);
        }
        return field;
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

    private static Class<?> resolveEntityType(Class<?> daoClass) {
        Class<?> resolved = resolveFromType(daoClass, Map.of());
        if (resolved != null) {
            return resolved;
        }
        throw new IllegalStateException("Cannot resolve entity type for dao: " + daoClass.getName());
    }

    private static Class<?> resolveFromType(Type type, Map<TypeVariable<?>, Type> mappings) {
        if (type instanceof Class<?> rawClass) {
            for (Type iface : rawClass.getGenericInterfaces()) {
                Class<?> resolved = resolveFromType(iface, mappings);
                if (resolved != null) {
                    return resolved;
                }
            }
            Type superType = rawClass.getGenericSuperclass();
            if (superType != null) {
                return resolveFromType(superType, mappings);
            }
            return null;
        }
        if (!(type instanceof ParameterizedType parameterized)) {
            return null;
        }
        Type raw = parameterized.getRawType();
        if (!(raw instanceof Class<?> rawClass)) {
            return null;
        }
        Map<TypeVariable<?>, Type> resolvedMappings = mergeMappings(rawClass, parameterized, mappings);
        if (rawClass == BaseDao.class) {
            Type arg = parameterized.getActualTypeArguments()[0];
            return resolveTypeArgument(arg, resolvedMappings);
        }
        for (Type iface : rawClass.getGenericInterfaces()) {
            Class<?> resolved = resolveFromType(iface, resolvedMappings);
            if (resolved != null) {
                return resolved;
            }
        }
        Type superType = rawClass.getGenericSuperclass();
        if (superType != null) {
            return resolveFromType(superType, resolvedMappings);
        }
        return null;
    }

    private static Map<TypeVariable<?>, Type> mergeMappings(
        Class<?> rawClass,
        ParameterizedType parameterized,
        Map<TypeVariable<?>, Type> mappings
    ) {
        TypeVariable<?>[] variables = rawClass.getTypeParameters();
        Type[] arguments = parameterized.getActualTypeArguments();
        Map<TypeVariable<?>, Type> merged = new LinkedHashMap<>(mappings);
        for (int i = 0; i < variables.length; i++) {
            Type argument = arguments[i];
            if (argument instanceof TypeVariable<?> variable && mappings.containsKey(variable)) {
                argument = mappings.get(variable);
            }
            merged.put(variables[i], argument);
        }
        return merged;
    }

    private static Class<?> resolveTypeArgument(Type type, Map<TypeVariable<?>, Type> mappings) {
        if (type instanceof Class<?> rawClass) {
            return rawClass;
        }
        if (type instanceof ParameterizedType parameterized) {
            Type raw = parameterized.getRawType();
            if (raw instanceof Class<?> rawClass) {
                return rawClass;
            }
        }
        if (type instanceof TypeVariable<?> variable) {
            Type mapped = mappings.get(variable);
            if (mapped != null && mapped != variable) {
                return resolveTypeArgument(mapped, mappings);
            }
        }
        return null;
    }

    private static DaoContext findContextField(Object target) {
        DaoContext context = findField(target, DaoContext.class, "daoContext");
        if (context != null) {
            return context;
        }
        return findField(target, DaoContext.class, null);
    }

    private static <T> T requireField(Object target, Class<T> type, String name) {
        T value = findField(target, type, name);
        if (value != null) {
            return value;
        }
        value = findField(target, type, null);
        if (value != null) {
            return value;
        }
        throw new IllegalStateException("Missing field " + type.getName() + " on " + target.getClass().getName());
    }

    private static <T> T findField(Object target, Class<T> type, String name) {
        Field field = name == null ? findFieldByType(target.getClass(), type) : findFieldByName(target.getClass(), name);
        if (field == null) {
            return null;
        }
        if (!type.isAssignableFrom(field.getType())) {
            throw new IllegalStateException(
                "Field " + field.getName() + " on " + target.getClass().getName() + " is not a " + type.getName()
            );
        }
        try {
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Failed to read field " + field.getName(), ex);
        }
    }

    private static Field findFieldByName(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ex) {
                // continue searching
            }
        }
        return null;
    }

    private static Field findFieldByType(Class<?> type, Class<?> fieldType) {
        Field found = null;
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!fieldType.isAssignableFrom(field.getType())) {
                    continue;
                }
                if (found != null) {
                    throw new IllegalStateException(
                        "Multiple fields of type " + fieldType.getName() + " found on " + type.getName()
                    );
                }
                found = field;
            }
        }
        return found;
    }
}

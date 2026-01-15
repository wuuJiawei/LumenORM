package io.lighting.lumen.template;

import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.db.Query;
import io.lighting.lumen.jdbc.RowMapper;
import io.lighting.lumen.jdbc.RowMappers;
import io.lighting.lumen.meta.EntityMetaRegistry;
import io.lighting.lumen.page.PageRequest;
import io.lighting.lumen.page.PageResult;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.RenderedPagination;
import io.lighting.lumen.template.annotations.SqlTemplate;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Runtime SQL template mapper factory.
 * <p>
 * When no APT-generated implementation is present, this factory creates a dynamic proxy
 * that interprets {@link SqlTemplate} annotations at runtime, similar to MyBatis mappers.
 */
public final class SqlTemplateProxyFactory {
    private SqlTemplateProxyFactory() {
    }

    /**
     * Create a dynamic proxy for a {@link SqlTemplate}-annotated interface.
     *
     * @param daoType           mapper interface
     * @param db                database handle used for execution
     * @param dialect           dialect for pagination / identifier rendering
     * @param metaRegistry      entity meta registry
     * @param entityNameResolver entity name resolver for @table/@col macros
     * @param <T>               mapper type
     * @return proxy instance
     */
    public static <T> T create(
        Class<T> daoType,
        Db db,
        io.lighting.lumen.sql.Dialect dialect,
        EntityMetaRegistry metaRegistry,
        EntityNameResolver entityNameResolver
    ) {
        Objects.requireNonNull(daoType, "daoType");
        Objects.requireNonNull(db, "db");
        Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(metaRegistry, "metaRegistry");
        Objects.requireNonNull(entityNameResolver, "entityNameResolver");
        if (!daoType.isInterface()) {
            throw new IllegalArgumentException("DAO type must be an interface: " + daoType.getName());
        }
        SqlTemplateInvocationHandler handler = new SqlTemplateInvocationHandler(
            daoType,
            db,
            dialect,
            metaRegistry,
            entityNameResolver
        );
        Object proxy = Proxy.newProxyInstance(
            daoType.getClassLoader(),
            new Class<?>[] { daoType },
            handler
        );
        return daoType.cast(proxy);
    }

    private static final class SqlTemplateInvocationHandler implements InvocationHandler {
        private final Db db;
        private final io.lighting.lumen.sql.Dialect dialect;
        private final EntityMetaRegistry metaRegistry;
        private final EntityNameResolver entityNameResolver;
        private final Map<Method, MethodHandler> handlers;

        private SqlTemplateInvocationHandler(
            Class<?> daoType,
            Db db,
            io.lighting.lumen.sql.Dialect dialect,
            EntityMetaRegistry metaRegistry,
            EntityNameResolver entityNameResolver
        ) {
            this.db = db;
            this.dialect = dialect;
            this.metaRegistry = metaRegistry;
            this.entityNameResolver = entityNameResolver;
            this.handlers = buildHandlers(daoType);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            MethodHandler handler = handlers.get(method);
            if (handler == null) {
                if (method.isDefault()) {
                    return invokeDefault(proxy, method, args);
                }
                throw new IllegalStateException("Missing @SqlTemplate method: " + method.getName());
            }
            return handler.invoke(
                db,
                dialect,
                metaRegistry,
                entityNameResolver,
                args == null ? new Object[0] : args
            );
        }

        private static Object invokeDefault(Object proxy, Method method, Object[] args) throws Throwable {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(method.getDeclaringClass(), lookup);
            return privateLookup
                .findSpecial(
                    method.getDeclaringClass(),
                    method.getName(),
                    MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
                    method.getDeclaringClass()
                )
                .bindTo(proxy)
                .invokeWithArguments(args == null ? new Object[0] : args);
        }

        private static Map<Method, MethodHandler> buildHandlers(Class<?> daoType) {
            Map<Method, MethodHandler> handlers = new HashMap<>();
            for (Method method : daoType.getMethods()) {
                if (method.isDefault() || method.getDeclaringClass() == Object.class) {
                    continue;
                }
                // Every abstract method must declare a SQL template.
                SqlTemplate annotation = method.getAnnotation(SqlTemplate.class);
                if (annotation == null) {
                    throw new IllegalArgumentException("@SqlTemplate is required on: " + method.getName());
                }
                handlers.put(method, MethodHandler.from(method, annotation.value()));
            }
            return handlers;
        }
    }

    private enum ReturnKind {
        LIST,
        PAGE,
        INT,
        LONG,
        VOID,
        RENDERED_SQL,
        QUERY,
        COMMAND,
        SINGLE
    }

    private record MethodHandler(
        io.lighting.lumen.template.SqlTemplate template,
        ReturnKind returnKind,
        int rowMapperIndex,
        Class<?> resultType,
        boolean selectLike,
        PageExpression pageExpression,
        PageParam pageParam,
        String[] bindingNames,
        int[] bindingIndexes
    ) {
        private static MethodHandler from(Method method, String templateText) {
            Objects.requireNonNull(method, "method");
            Objects.requireNonNull(templateText, "templateText");
            validateThrows(method);

            // Validate template usage up front to fail fast on missing bindings.
            SqlTemplateAnalysis analysis = SqlTemplateAnalyzer.analyze(templateText);
            if (analysis.orderByHasParams()) {
                throw new IllegalArgumentException("@orderBy fragments must not use parameters");
            }

            io.lighting.lumen.template.SqlTemplate parsedTemplate = io.lighting.lumen.template.SqlTemplate.parse(templateText);
            ReturnDescriptor descriptor = resolveReturnDescriptor(method, templateText, parsedTemplate);
            int rowMapperIndex = findRowMapperIndex(method);
            if (rowMapperIndex < 0 && descriptor.returnKind == ReturnKind.LIST && descriptor.resultType == null) {
                throw new IllegalArgumentException("List-returning @SqlTemplate methods require a RowMapper parameter");
            }
            PageParam pageParam = findPageParam(method);
            if (descriptor.returnKind == ReturnKind.PAGE && descriptor.pageExpression == null && pageParam == null) {
                throw new IllegalArgumentException(
                    "@page is required for PageResult return types unless a PageRequest/Page parameter is present"
                );
            }
            if (descriptor.returnKind == ReturnKind.PAGE && descriptor.resultType == null) {
                throw new IllegalArgumentException("PageResult return type must declare an element type");
            }

            BindingInfo bindingInfo = resolveBindings(method, analysis.bindings(), rowMapperIndex);
            return new MethodHandler(
                parsedTemplate,
                descriptor.returnKind,
                rowMapperIndex,
                descriptor.resultType,
                descriptor.selectLike,
                descriptor.pageExpression,
                pageParam,
                bindingInfo.bindingNames,
                bindingInfo.bindingIndexes
            );
        }

        private Object invoke(
            Db db,
            io.lighting.lumen.sql.Dialect dialect,
            EntityMetaRegistry metaRegistry,
            EntityNameResolver entityNameResolver,
            Object[] args
        ) throws SQLException {
            Bindings bindings = buildBindings(args);
            // Template rendering needs bindings + dialect + entity metadata.
            TemplateContext context = new TemplateContext(
                bindings.asMap(),
                dialect,
                metaRegistry,
                entityNameResolver
            );
            RenderedSql rendered = template.render(context);
            return execute(db, rendered, bindings, context, dialect, metaRegistry, entityNameResolver, args);
        }

        private Bindings buildBindings(Object[] args) {
            if (bindingNames.length == 0) {
                return Bindings.empty();
            }
            Object firstValue = args[bindingIndexes[0]];
            if (bindingNames.length == 1) {
                return Bindings.of(bindingNames[0], firstValue);
            }
            // Build name/value pairs in declaration order to keep predictable binding keys.
            Object[] more = new Object[(bindingNames.length - 1) * 2];
            int offset = 0;
            for (int i = 1; i < bindingNames.length; i++) {
                more[offset++] = bindingNames[i];
                more[offset++] = args[bindingIndexes[i]];
            }
            return Bindings.of(bindingNames[0], firstValue, more);
        }

        private Object execute(
            Db db,
            RenderedSql rendered,
            Bindings bindings,
            TemplateContext context,
            io.lighting.lumen.sql.Dialect dialect,
            EntityMetaRegistry metaRegistry,
            EntityNameResolver entityNameResolver,
            Object[] args
        ) throws SQLException {
            switch (returnKind) {
                case LIST -> {
                    return fetchList(db, rendered, args);
                }
                case PAGE -> {
                    PageValues values = resolvePageValues(context, args);
                    RenderedSql paged = pageExpression == null
                        ? applyPagination(rendered, values, dialect)
                        : rendered;
                    List<Object> items = fetchList(db, paged, args);
                    long total = fetchCount(db, renderCount(bindings, dialect, metaRegistry, entityNameResolver));
                    return new PageResult<>(items, values.page(), values.pageSize(), total);
                }
                case INT -> {
                    return selectLike ? fetchInt(db, rendered) : db.execute(Command.of(rendered));
                }
                case LONG -> {
                    return selectLike ? fetchLong(db, rendered) : (long) db.execute(Command.of(rendered));
                }
                case VOID -> {
                    db.execute(Command.of(rendered));
                    return null;
                }
                case RENDERED_SQL -> {
                    return rendered;
                }
                case QUERY -> {
                    return Query.of(rendered);
                }
                case COMMAND -> {
                    return Command.of(rendered);
                }
                case SINGLE -> {
                    List<Object> rows = fetchList(db, rendered, args);
                    return rows.isEmpty() ? null : rows.get(0);
                }
                default -> throw new IllegalArgumentException("Unsupported return type: " + returnKind);
            }
        }

        private List<Object> fetchList(Db db, RenderedSql rendered, Object[] args) throws SQLException {
            RowMapper<Object> mapper = resolveRowMapper(args);
            return db.fetch(Query.of(rendered), mapper);
        }

        private RowMapper<Object> resolveRowMapper(Object[] args) {
            if (rowMapperIndex >= 0) {
                @SuppressWarnings("unchecked")
                RowMapper<Object> mapper = (RowMapper<Object>) args[rowMapperIndex];
                return mapper;
            }
            if (resultType == null) {
                throw new IllegalStateException("RowMapper is required when result type is unknown");
            }
            @SuppressWarnings("unchecked")
            RowMapper<Object> mapper = (RowMapper<Object>) RowMappers.auto(resultType);
            return mapper;
        }

        private int fetchInt(Db db, RenderedSql rendered) throws SQLException {
            List<Integer> rows = db.fetch(Query.of(rendered), rs -> rs.getInt(1));
            if (rows.isEmpty()) {
                return 0;
            }
            return rows.get(0);
        }

        private long fetchLong(Db db, RenderedSql rendered) throws SQLException {
            List<Long> rows = db.fetch(Query.of(rendered), rs -> rs.getLong(1));
            if (rows.isEmpty()) {
                return 0L;
            }
            return rows.get(0);
        }

        private long fetchCount(Db db, RenderedSql rendered) throws SQLException {
            List<Long> rows = db.fetch(Query.of(rendered), rs -> rs.getLong(1));
            if (rows.isEmpty()) {
                return 0L;
            }
            return rows.get(0);
        }

        private RenderedSql renderCount(
            Bindings bindings,
            io.lighting.lumen.sql.Dialect dialect,
            EntityMetaRegistry metaRegistry,
            EntityNameResolver entityNameResolver
        ) {
            // Render without LIMIT/OFFSET, then wrap as SELECT COUNT(*).
            TemplateContext countContext = new TemplateContext(
                bindings.asMap(),
                new NoPagingDialect(dialect),
                metaRegistry,
                entityNameResolver
            );
            RenderedSql base = template.render(countContext);
            String sql = wrapCountSql(base.sql());
            return new RenderedSql(sql, base.binds());
        }

        private String wrapCountSql(String sql) {
            String trimmed = sql == null ? "" : sql.trim();
            if (trimmed.endsWith(";")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1);
            }
            return "SELECT COUNT(*) FROM (" + trimmed + ") AS count_src";
        }

        private PageValues resolvePageValues(TemplateContext context, Object[] args) {
            if (pageExpression != null) {
                int page = toInt(pageExpression.page().evaluate(context), "page");
                int pageSize = toInt(pageExpression.pageSize().evaluate(context), "pageSize");
                return new PageValues(page, pageSize);
            }
            if (pageParam != null) {
                return pageParam.extract(args);
            }
            throw new IllegalStateException("@page is required for PageResult return types");
        }

        private RenderedSql applyPagination(
            RenderedSql rendered,
            PageValues values,
            io.lighting.lumen.sql.Dialect dialect
        ) {
            RenderedPagination pagination = dialect.renderPagination(values.page(), values.pageSize(), List.of());
            if (pagination.sqlFragment().isBlank()) {
                return rendered;
            }
            String baseSql = rendered.sql();
            String fragment = pagination.sqlFragment();
            String combined = combineSql(baseSql, fragment);
            List<io.lighting.lumen.sql.Bind> combinedBinds = new java.util.ArrayList<>(rendered.binds());
            combinedBinds.addAll(pagination.binds());
            return new RenderedSql(combined, combinedBinds);
        }

        private String combineSql(String baseSql, String fragment) {
            if (baseSql == null || baseSql.isBlank()) {
                return fragment.trim();
            }
            String left = baseSql;
            String right = fragment;
            if (Character.isWhitespace(left.charAt(left.length() - 1))) {
                right = right.stripLeading();
            } else if (!right.isBlank() && !Character.isWhitespace(right.charAt(0))) {
                left = left + " ";
            }
            return left + right;
        }

        private int toInt(Object value, String name) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String str) {
                try {
                    return Integer.parseInt(str);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("Invalid " + name + " value: " + value);
                }
            }
            throw new IllegalArgumentException("Invalid " + name + " value: " + value);
        }

        private static void validateThrows(Method method) {
            for (Class<?> ex : method.getExceptionTypes()) {
                if (SQLException.class.isAssignableFrom(ex)) {
                    return;
                }
            }
            throw new IllegalArgumentException("@SqlTemplate methods must declare throws SQLException");
        }

        private static ReturnDescriptor resolveReturnDescriptor(
            Method method,
            String templateText,
            io.lighting.lumen.template.SqlTemplate parsedTemplate
        ) {
            // Decide how to execute based on the declared return type.
            Class<?> returnType = method.getReturnType();
            if (returnType == void.class) {
                return new ReturnDescriptor(ReturnKind.VOID, null, false, null);
            }
            if (returnType == int.class || returnType == Integer.class) {
                return new ReturnDescriptor(ReturnKind.INT, null, isSelectLike(templateText), null);
            }
            if (returnType == long.class || returnType == Long.class) {
                return new ReturnDescriptor(ReturnKind.LONG, null, isSelectLike(templateText), null);
            }
            if (returnType == RenderedSql.class) {
                return new ReturnDescriptor(ReturnKind.RENDERED_SQL, null, false, null);
            }
            if (returnType == Query.class) {
                return new ReturnDescriptor(ReturnKind.QUERY, null, false, null);
            }
            if (returnType == Command.class) {
                return new ReturnDescriptor(ReturnKind.COMMAND, null, false, null);
            }
            if (PageResult.class.isAssignableFrom(returnType)) {
                Class<?> elementType = resolveElementType(method, "PageResult");
                PageExpression pageExpression = findPageExpression(parsedTemplate.nodes());
                return new ReturnDescriptor(ReturnKind.PAGE, elementType, false, pageExpression);
            }
            if (List.class.isAssignableFrom(returnType)) {
                Class<?> elementType = resolveElementType(method, "List");
                return new ReturnDescriptor(ReturnKind.LIST, elementType, false, null);
            }
            return new ReturnDescriptor(ReturnKind.SINGLE, returnType, false, null);
        }

        private static boolean isSelectLike(String sql) {
            if (sql == null) {
                return false;
            }
            String trimmed = sql.stripLeading();
            return startsWithIgnoreCase(trimmed, "select") || startsWithIgnoreCase(trimmed, "with");
        }

        private static boolean startsWithIgnoreCase(String value, String prefix) {
            if (value.length() < prefix.length()) {
                return false;
            }
            return value.regionMatches(true, 0, prefix, 0, prefix.length());
        }

        private static Class<?> resolveElementType(Method method, String label) {
            Type generic = method.getGenericReturnType();
            if (generic instanceof ParameterizedType parameterized) {
                Type[] args = parameterized.getActualTypeArguments();
                if (args.length == 1) {
                    Class<?> raw = rawClass(args[0]);
                    if (raw != null) {
                        return raw;
                    }
                }
            }
            return null;
        }

        private static Class<?> rawClass(Type type) {
            if (type instanceof Class<?> clazz) {
                return clazz;
            }
            if (type instanceof ParameterizedType parameterized) {
                Type raw = parameterized.getRawType();
                if (raw instanceof Class<?> clazz) {
                    return clazz;
                }
            }
            return null;
        }

        private static PageExpression findPageExpression(List<TemplateNode> nodes) {
            for (TemplateNode node : nodes) {
                if (node instanceof PageNode pageNode) {
                    return new PageExpression(pageNode.page(), pageNode.pageSize());
                }
                // Recursively scan nested blocks for @page nodes.
                PageExpression nested = null;
                if (node instanceof IfNode ifNode) {
                    nested = findPageExpression(ifNode.body());
                } else if (node instanceof ForNode forNode) {
                    nested = findPageExpression(forNode.body());
                } else if (node instanceof ClauseNode clauseNode) {
                    nested = findPageExpression(clauseNode.body());
                } else if (node instanceof OrNode orNode) {
                    nested = findPageExpression(orNode.body());
                }
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }

        private static int findRowMapperIndex(Method method) {
            int rowMapperIndex = -1;
            Parameter[] parameters = method.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                if (RowMapper.class.isAssignableFrom(parameters[i].getType())) {
                    if (rowMapperIndex >= 0) {
                        throw new IllegalArgumentException("@SqlTemplate methods must declare at most one RowMapper parameter");
                    }
                    rowMapperIndex = i;
                }
            }
            return rowMapperIndex;
        }

        private static PageParam findPageParam(Method method) {
            Parameter[] parameters = method.getParameters();
            PageParam pageParam = null;
            for (int i = 0; i < parameters.length; i++) {
                Class<?> type = parameters[i].getType();
                PageParam candidate = null;
                if (PageRequest.class.isAssignableFrom(type)) {
                    candidate = new PageParam(i, PageParamKind.REQUEST);
                } else if (io.lighting.lumen.active.Page.class.isAssignableFrom(type)) {
                    candidate = new PageParam(i, PageParamKind.ACTIVE);
                }
                if (candidate != null) {
                    if (pageParam != null) {
                        throw new IllegalArgumentException(
                            "@SqlTemplate methods must declare at most one PageRequest/Page parameter"
                        );
                    }
                    pageParam = candidate;
                }
            }
            return pageParam;
        }

        private static BindingInfo resolveBindings(Method method, Set<String> bindings, int rowMapperIndex) {
            Parameter[] parameters = method.getParameters();
            Map<String, Integer> names = new LinkedHashMap<>();
            for (int i = 0; i < parameters.length; i++) {
                if (i == rowMapperIndex) {
                    continue;
                }
                Parameter parameter = parameters[i];
                if (!parameter.isNamePresent()) {
                    // Runtime binding relies on Java parameter names; enable -parameters at compile time.
                    throw new IllegalArgumentException(
                        "Parameter names are not available for @SqlTemplate. Compile with -parameters: "
                            + method.getName()
                    );
                }
                String name = parameter.getName();
                if ("__dialect".equals(name)) {
                    throw new IllegalArgumentException("Binding name is reserved: " + name);
                }
                names.put(name, i);
            }
            for (String binding : bindings) {
                if (!names.containsKey(binding)) {
                    throw new IllegalArgumentException("Missing template bindings: " + binding);
                }
            }
            String[] bindingNames = names.keySet().toArray(new String[0]);
            int[] bindingIndexes = new int[bindingNames.length];
            for (int i = 0; i < bindingNames.length; i++) {
                bindingIndexes[i] = names.get(bindingNames[i]);
            }
            return new BindingInfo(bindingNames, bindingIndexes);
        }

        private record BindingInfo(String[] bindingNames, int[] bindingIndexes) {
        }

        private record ReturnDescriptor(
            ReturnKind returnKind,
            Class<?> resultType,
            boolean selectLike,
            PageExpression pageExpression
        ) {
        }

        private record PageExpression(TemplateExpression page, TemplateExpression pageSize) {
        }

        private record PageValues(int page, int pageSize) {
        }

        private enum PageParamKind {
            REQUEST,
            ACTIVE
        }

        private record PageParam(int index, PageParamKind kind) {
            private PageValues extract(Object[] args) {
                Object value = args[index];
                if (value == null) {
                    throw new IllegalArgumentException("Page parameter must not be null");
                }
                if (kind == PageParamKind.REQUEST) {
                    PageRequest request = (PageRequest) value;
                    return new PageValues(request.page(), request.pageSize());
                }
                if (kind == PageParamKind.ACTIVE) {
                    io.lighting.lumen.active.Page page = (io.lighting.lumen.active.Page) value;
                    return new PageValues(page.page(), page.pageSize());
                }
                throw new IllegalStateException("Unsupported page parameter kind: " + kind);
            }
        }
    }

    private static final class NoPagingDialect implements io.lighting.lumen.sql.Dialect {
        private final io.lighting.lumen.sql.Dialect delegate;

        private NoPagingDialect(io.lighting.lumen.sql.Dialect delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public String id() {
            return delegate.id();
        }

        @Override
        public String quoteIdent(String ident) {
            return delegate.quoteIdent(ident);
        }

        @Override
        public RenderedPagination renderPagination(int page, int pageSize, List<io.lighting.lumen.sql.ast.OrderItem> orderBy) {
            return new RenderedPagination("", List.of());
        }

        @Override
        public RenderedSql renderFunction(String name, List<RenderedSql> args) {
            return delegate.renderFunction(name, args);
        }
    }
}

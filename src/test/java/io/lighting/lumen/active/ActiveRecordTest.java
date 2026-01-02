package io.lighting.lumen.active;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.DefaultDb;
import io.lighting.lumen.db.Query;
import io.lighting.lumen.dsl.Dsl;
import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.IdStrategy;
import io.lighting.lumen.meta.LogicDelete;
import io.lighting.lumen.meta.ReflectionEntityMetaRegistry;
import io.lighting.lumen.meta.Table;
import io.lighting.lumen.sql.Bindings;
import io.lighting.lumen.sql.Dialect;
import io.lighting.lumen.sql.RenderedPagination;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.sql.SqlRenderer;
import java.sql.SQLException;
import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActiveRecordTest {
    private DefaultDb db;
    private SqlRenderer renderer;

    @BeforeEach
    void setUp() throws SQLException {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:active_record;MODE=MySQL;DB_CLOSE_DELAY=-1");
        renderer = new SqlRenderer(new NoQuoteDialect());
        db = new DefaultDb(
            new io.lighting.lumen.jdbc.JdbcExecutor(dataSource),
            renderer,
            new NoQuoteDialect(),
            new ReflectionEntityMetaRegistry(),
            name -> {
                throw new IllegalArgumentException("Unknown entity type: " + name);
            }
        );
        ActiveRecord.configure(ActiveRecordConfig.builder()
            .db(db)
            .renderer(renderer)
            .metaRegistry(new ReflectionEntityMetaRegistry())
            .build());
        prepareSchema();
    }

    @Test
    void insertUpdateDeleteAndCallbacks() throws SQLException {
        OrderRecord order = new OrderRecord();
        order.status = "NEW";
        boolean inserted = order.insert();
        assertTrue(inserted);
        assertNotNull(order.id);
        assertEquals(1, order.beforeInsertCount);
        assertEquals(1, order.afterInsertCount);

        order.status = "PAID";
        boolean updated = order.updateById();
        assertTrue(updated);
        assertEquals(1, order.beforeUpdateCount);
        assertEquals(1, order.afterUpdateCount);

        boolean deleted = order.deleteById();
        assertTrue(deleted);
        assertEquals(1, order.beforeDeleteCount);
        assertEquals(1, order.afterDeleteCount);

        OrderRecord found = order.selectById(order.id);
        assertNull(found);

        int deletedFlag = loadDeletedFlag(order.id);
        assertEquals(1, deletedFlag);
    }

    @Test
    void saveSwitchesInsertAndUpdate() throws SQLException {
        OrderRecord order = new OrderRecord();
        order.status = "NEW";
        order.insertOrUpdate();
        assertTrue(order.id > 0);
        assertEquals(1, order.beforeSaveCount);
        assertEquals(1, order.afterSaveCount);

        order.status = "PAID";
        order.insertOrUpdate();
        OrderRecord found = order.selectById(order.id);
        assertEquals("PAID", found.status);
        assertEquals(2, order.beforeSaveCount);
        assertEquals(2, order.afterSaveCount);
    }

    @Test
    void supportsActiveQueryDsl() throws SQLException {
        OrderRecord order = new OrderRecord();
        order.status = "NEW";
        order.insert();

        OrderRecord other = new OrderRecord();
        other.status = "OLD";
        other.insert();

        List<OrderRecord> results = Model.of(OrderRecord.class)
            .select(OrderRecord::id, OrderRecord::status)
            .where(OrderRecord::status).eq("NEW")
            .objList();
        assertEquals(1, results.size());

        boolean updated = Model.of(OrderRecord.class)
            .set(OrderRecord::status, "PAID")
            .where(OrderRecord::id).eq(order.id)
            .update();
        assertTrue(updated);

        OrderRecord loaded = Model.of(OrderRecord.class)
            .where(OrderRecord::id).eq(order.id)
            .one();
        assertEquals("PAID", loaded.status);

        boolean removed = Model.of(OrderRecord.class)
            .where(OrderRecord::id).eq(order.id)
            .remove();
        assertTrue(removed);

        OrderRecord afterDelete = Model.of(OrderRecord.class)
            .where(OrderRecord::id).eq(order.id)
            .one();
        assertNull(afterDelete);
    }

    @Test
    void supportsActiveQuerySaveAndRemoveById() throws SQLException {
        boolean saved = Model.of(OrderRecord.class)
            .set(OrderRecord::status, "NEW")
            .save();
        assertTrue(saved);

        OrderRecord loaded = Model.of(OrderRecord.class)
            .where(OrderRecord::status).eq("NEW")
            .one();
        assertNotNull(loaded);

        boolean removed = Model.of(OrderRecord.class)
            .set(OrderRecord::id, loaded.id)
            .removeById();
        assertTrue(removed);

        OrderRecord afterDelete = Model.of(OrderRecord.class)
            .where(OrderRecord::id).eq(loaded.id)
            .one();
        assertNull(afterDelete);
    }

    @Test
    void supportsActiveQueryJoin() throws SQLException {
        OrderRecord order = new OrderRecord();
        order.status = "NEW";
        order.insert();

        OrderItemRecord item = new OrderItemRecord();
        item.orderId = order.id;
        item.sku = "SKU-1";
        item.insert();

        ActiveQuery<OrderRecord> query = Model.of(OrderRecord.class);
        io.lighting.lumen.dsl.Table orders = query.table();
        io.lighting.lumen.dsl.Table items = query.table(OrderItemRecord.class).as("oi");
        List<OrderRecord> rows = query
            .select(orders.col(OrderRecord::id), orders.col(OrderRecord::status), items.col("sku"))
            .leftJoin(items).on(items.col("orderId").eq(orders.col(OrderRecord::id)))
            .where(orders.col(OrderRecord::id)).eq(order.id)
            .objList();
        assertEquals(1, rows.size());
    }

    @Test
    void supportsRelations() throws SQLException {
        OrderRecord order = new OrderRecord();
        order.status = "NEW";
        order.insert();

        OrderItemRecord item1 = new OrderItemRecord();
        item1.orderId = order.id;
        item1.sku = "A";
        item1.insert();

        OrderItemRecord item2 = new OrderItemRecord();
        item2.orderId = order.id;
        item2.sku = "B";
        item2.insert();

        List<OrderItemRecord> items = order.hasMany(OrderItemRecord.class, OrderItemRecord::orderId);
        assertEquals(2, items.size());

        OrderRecord parent = item1.belongsTo(OrderRecord.class, OrderItemRecord::orderId, OrderRecord::id);
        assertNotNull(parent);
        assertEquals(order.id, parent.id);
    }

    @Test
    void supportsHasOne() throws SQLException {
        OrderRecord order = new OrderRecord();
        order.status = "NEW";
        order.insert();

        OrderItemRecord item = new OrderItemRecord();
        item.orderId = order.id;
        item.sku = "A";
        item.insert();

        OrderItemRecord one = order.hasOne(OrderItemRecord.class, OrderItemRecord::orderId);
        assertNotNull(one);
        assertEquals(order.id, one.orderId);
    }

    @Test
    void generatesUuidIds() throws SQLException {
        UuidOrder record = new UuidOrder();
        record.status = "NEW";
        record.insert();
        assertNotNull(record.id);
        assertTrue(record.id.length() >= 32);
    }

    @Test
    void listsAndHardDeletesWithoutLogicDelete() throws SQLException {
        OrderItemRecord item1 = new OrderItemRecord();
        item1.orderId = 1L;
        item1.sku = "X";
        item1.insert();

        OrderItemRecord item2 = new OrderItemRecord();
        item2.orderId = 2L;
        item2.sku = "Y";
        item2.insert();

        List<OrderItemRecord> all = item1.selectAll();
        assertEquals(2, all.size());

        boolean deleted = item1.deleteById();
        assertTrue(deleted);

        OrderItemRecord remaining = item1.selectById(item1.id);
        assertNull(remaining);
        assertEquals(1, item1.selectAll().size());
    }

    private int loadDeletedFlag(long id) throws SQLException {
        Dsl dsl = new Dsl(new ReflectionEntityMetaRegistry());
        io.lighting.lumen.dsl.Table table = dsl.table(OrderRecord.class);
        RenderedSql rendered = renderer.render(
            dsl.select(table.col("deleted").select())
                .from(table)
                .where(table.col("id").eq(id))
                .build(),
            Bindings.empty()
        );
        return db.fetch(Query.of(rendered), rs -> rs.getInt(1)).get(0);
    }

    private void prepareSchema() throws SQLException {
        db.execute(Command.of(new RenderedSql("DROP TABLE IF EXISTS orders", List.of())));
        db.execute(Command.of(new RenderedSql("DROP TABLE IF EXISTS order_items", List.of())));
        db.execute(Command.of(new RenderedSql("DROP TABLE IF EXISTS uuid_orders", List.of())));
        db.execute(Command.of(new RenderedSql(
            "CREATE TABLE orders (id BIGINT AUTO_INCREMENT PRIMARY KEY, status VARCHAR(16), deleted INT)",
            List.of()
        )));
        db.execute(Command.of(new RenderedSql(
            "CREATE TABLE order_items (id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT, sku VARCHAR(16))",
            List.of()
        )));
        db.execute(Command.of(new RenderedSql(
            "CREATE TABLE uuid_orders (id VARCHAR(64) PRIMARY KEY, status VARCHAR(16))",
            List.of()
        )));
    }

    private static final class NoQuoteDialect implements Dialect {
        @Override
        public String id() {
            return "plain";
        }

        @Override
        public String quoteIdent(String ident) {
            return ident;
        }

        @Override
        public RenderedPagination renderPagination(int page, int pageSize, List<io.lighting.lumen.sql.ast.OrderItem> orderBy) {
            throw new UnsupportedOperationException("Pagination not supported in active record tests");
        }
    }

    @Table(name = "orders")
    private static final class OrderRecord extends Model<OrderRecord> {
        @Id(strategy = IdStrategy.AUTO)
        @Column(name = "id")
        private Long id;

        @Column(name = "status")
        private String status;

        @LogicDelete(active = "0", deleted = "1")
        @Column(name = "deleted")
        private Integer deleted;

        private int beforeInsertCount;
        private int afterInsertCount;
        private int beforeUpdateCount;
        private int afterUpdateCount;
        private int beforeDeleteCount;
        private int afterDeleteCount;
        private int beforeSaveCount;
        private int afterSaveCount;

        public Long id() {
            return id;
        }

        public String status() {
            return status;
        }

        @Override
        protected void beforeInsert() {
            beforeInsertCount++;
        }

        @Override
        protected void afterInsert(int rows) {
            afterInsertCount++;
        }

        @Override
        protected void beforeUpdate() {
            beforeUpdateCount++;
        }

        @Override
        protected void afterUpdate(int rows) {
            afterUpdateCount++;
        }

        @Override
        protected void beforeDelete(boolean logical) {
            beforeDeleteCount++;
        }

        @Override
        protected void afterDelete(int rows, boolean logical) {
            afterDeleteCount++;
        }

        @Override
        protected void beforeSave() {
            beforeSaveCount++;
        }

        @Override
        protected void afterSave(int rows) {
            afterSaveCount++;
        }
    }

    @Table(name = "order_items")
    private static final class OrderItemRecord extends Model<OrderItemRecord> {
        @Id(strategy = IdStrategy.AUTO)
        @Column(name = "id")
        private Long id;

        @Column(name = "order_id")
        private Long orderId;

        @Column(name = "sku")
        private String sku;

        public Long orderId() {
            return orderId;
        }
    }

    @Table(name = "uuid_orders")
    private static final class UuidOrder extends Model<UuidOrder> {
        @Id(strategy = IdStrategy.UUID)
        @Column(name = "id")
        private String id;

        @Column(name = "status")
        private String status;

        public String id() {
            return id;
        }
    }
}

package io.lighting.lumen.template;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.db.Command;
import io.lighting.lumen.db.Query;
import io.lighting.lumen.jdbc.RowMapper;
import io.lighting.lumen.page.PageRequest;
import io.lighting.lumen.page.PageResult;
import io.lighting.lumen.page.Sort;
import io.lighting.lumen.sql.RenderedSql;
import io.lighting.lumen.template.annotations.SqlTemplate;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SqlTemplateProxyFactoryTest {
    @Test
    void executesSqlTemplateMethodsViaDynamicProxy() throws SQLException {
        Lumen lumen = Lumen.builder()
            .dataSource(dataSource())
            .build();

        lumen.db().execute(Command.of(new RenderedSql(
            "CREATE TABLE ITEMS (ID BIGINT AUTO_INCREMENT PRIMARY KEY, NAME VARCHAR(64))",
            List.of()
        )));

        ItemDao dao = lumen.dao(ItemDao.class);

        int inserted = dao.insert("alpha");
        assertEquals(1, inserted);

        dao.insertSilent("beta");

        List<String> names = dao.list(resultSet -> resultSet.getString(1));
        assertEquals(List.of("alpha", "beta"), names);

        List<Item> items = dao.listItems();
        assertEquals(2, items.size());

        Item alpha = dao.findItem("alpha");
        assertNotNull(alpha);
        assertEquals("alpha", alpha.name());

        PageResult<Item> page = dao.page(1, 1);
        assertEquals(1, page.items().size());
        assertEquals(2L, page.total());

        PageResult<Item> sorted = dao.pageAuto(PageRequest.of(1, 1, Sort.desc("name")));
        assertEquals(1, sorted.items().size());
        assertEquals("beta", sorted.items().get(0).name());
        assertEquals(2L, sorted.total());

        PageResult<Item> noCount = dao.pageAuto(PageRequest.of(1, 1, Sort.desc("name")).withoutCount());
        assertEquals(1, noCount.items().size());
        assertEquals(PageResult.TOTAL_UNKNOWN, noCount.total());

        assertEquals(2L, dao.countAll());
        assertEquals(2, dao.countAllInt());

        RenderedSql rendered = dao.findRendered("alpha");
        assertNotNull(rendered);
        List<String> renderedRows = lumen.db().fetch(Query.of(rendered), rs -> rs.getString(1));
        assertEquals(List.of("alpha"), renderedRows);

        Query query = dao.findQuery("beta");
        List<String> queryRows = lumen.db().fetch(query, rs -> rs.getString(1));
        assertEquals(List.of("beta"), queryRows);

        Command deleteCommand = dao.deleteCommand("alpha");
        int deleted = lumen.db().execute(deleteCommand);
        assertEquals(1, deleted);

        long deleteCount = dao.deleteCount("beta");
        assertEquals(1L, deleteCount);
    }

    private static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:template_proxy;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");
        return dataSource;
    }

    private interface ItemDao {
        @SqlTemplate("INSERT INTO ITEMS (NAME) VALUES (:name)")
        int insert(String name) throws SQLException;

        @SqlTemplate("INSERT INTO ITEMS (NAME) VALUES (:name)")
        void insertSilent(String name) throws SQLException;

        @SqlTemplate("SELECT NAME FROM ITEMS ORDER BY ID")
        List<String> list(RowMapper<String> mapper) throws SQLException;

        @SqlTemplate("SELECT ID, NAME FROM ITEMS ORDER BY ID")
        List<Item> listItems() throws SQLException;

        @SqlTemplate("SELECT ID, NAME FROM ITEMS WHERE NAME = :name")
        Item findItem(String name) throws SQLException;

        @SqlTemplate("SELECT ID, NAME FROM ITEMS ORDER BY ID @page(:page, :pageSize)")
        PageResult<Item> page(int page, int pageSize) throws SQLException;

        @SqlTemplate("SELECT ID, NAME FROM ITEMS @orderBy(:page.sort, allowed={name: NAME, id: ID})")
        PageResult<Item> pageAuto(PageRequest page) throws SQLException;

        @SqlTemplate("SELECT COUNT(*) FROM ITEMS")
        long countAll() throws SQLException;

        @SqlTemplate("SELECT COUNT(*) FROM ITEMS")
        int countAllInt() throws SQLException;

        @SqlTemplate("SELECT NAME FROM ITEMS WHERE NAME = :name")
        RenderedSql findRendered(String name) throws SQLException;

        @SqlTemplate("SELECT NAME FROM ITEMS WHERE NAME = :name")
        Query findQuery(String name) throws SQLException;

        @SqlTemplate("DELETE FROM ITEMS WHERE NAME = :name")
        Command deleteCommand(String name) throws SQLException;

        @SqlTemplate("DELETE FROM ITEMS WHERE NAME = :name")
        long deleteCount(String name) throws SQLException;
    }

    private record Item(long id, String name) {
    }
}

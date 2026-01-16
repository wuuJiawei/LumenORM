package io.lighting.lumen.dao;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.db.Command;
import io.lighting.lumen.meta.Column;
import io.lighting.lumen.meta.Id;
import io.lighting.lumen.meta.IdStrategy;
import io.lighting.lumen.meta.LogicDelete;
import io.lighting.lumen.meta.Table;
import io.lighting.lumen.page.PageRequest;
import io.lighting.lumen.page.PageResult;
import io.lighting.lumen.page.Sort;
import io.lighting.lumen.sql.RenderedSql;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BaseDaoTest {
    @Test
    void supportsCrudAndPaging() throws SQLException {
        Lumen lumen = Lumen.builder()
            .dataSource(dataSource())
            .build();

        lumen.db().execute(Command.of(new RenderedSql(
            "CREATE TABLE NOTES (" +
                "ID BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "TITLE VARCHAR(100), " +
                "CONTENT VARCHAR(200), " +
                "DELETED INT" +
            ")",
            List.of()
        )));

        NoteDao dao = lumen.dao(NoteDao.class);

        NoteEntity first = new NoteEntity();
        first.setTitle("B");
        first.setContent("first");
        assertEquals(1, dao.insert(first));
        assertNotNull(first.getId());
        assertEquals(Integer.valueOf(0), first.getDeleted());

        NoteEntity second = new NoteEntity();
        second.setTitle("A");
        second.setContent("second");
        assertEquals(1, dao.insert(second));

        assertEquals(2, dao.selectList().size());

        PageResult<NoteEntity> page = dao.selectPage(PageRequest.of(1, 1, Sort.desc("title")));
        assertEquals(1, page.items().size());
        assertEquals("B", page.items().get(0).getTitle());
        assertEquals(2L, page.total());

        PageResult<NoteEntity> pageNoCount = dao.selectPage(PageRequest.of(1, 1, Sort.asc("title")).withoutCount());
        assertEquals(PageResult.TOTAL_UNKNOWN, pageNoCount.total());

        first.setContent("updated");
        assertEquals(1, dao.updateById(first));
        NoteEntity updated = dao.selectById(first.getId());
        assertNotNull(updated);
        assertEquals("updated", updated.getContent());

        assertEquals(1, dao.deleteById(first.getId()));
        assertNull(dao.selectById(first.getId()));
        assertEquals(1, dao.selectList().size());
    }

    private static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:base_dao;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");
        return dataSource;
    }

    private interface NoteDao extends BaseDao<NoteEntity> {
    }

    @Table(name = "NOTES")
    private static class NoteEntity {
        @Id(strategy = IdStrategy.AUTO)
        @Column(name = "ID")
        private Long id;

        @Column(name = "TITLE")
        private String title;

        @Column(name = "CONTENT")
        private String content;

        @LogicDelete(active = "0", deleted = "1")
        @Column(name = "DELETED")
        private Integer deleted;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public Integer getDeleted() {
            return deleted;
        }

        public void setDeleted(Integer deleted) {
            this.deleted = deleted;
        }
    }
}

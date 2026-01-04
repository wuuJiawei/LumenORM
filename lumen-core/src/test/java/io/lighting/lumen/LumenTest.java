package io.lighting.lumen;

import io.lighting.lumen.meta.ReflectionEntityMetaRegistry;
import io.lighting.lumen.sql.Dialect;
import io.lighting.lumen.sql.dialect.LimitOffsetDialect;
import io.lighting.lumen.template.EntityNameResolvers;
import java.util.Map;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LumenTest {
    @Test
    void buildsContextWithDefaults() {
        Lumen context = Lumen.builder()
            .dataSource(dataSource())
            .dialect(dialect())
            .metaRegistry(new ReflectionEntityMetaRegistry())
            .entityNameResolver(EntityNameResolvers.from(Map.of()))
            .build();

        assertNotNull(context.db());
        assertNotNull(context.dsl());
        assertNotNull(context.renderer());
    }

    @Test
    void createsDaoFromGeneratedImpl() {
        Lumen context = Lumen.builder()
            .dataSource(dataSource())
            .dialect(dialect())
            .metaRegistry(new ReflectionEntityMetaRegistry())
            .entityNameResolver(EntityNameResolvers.from(Map.of()))
            .build();

        ExampleDao dao = context.dao(ExampleDao.class);

        assertTrue(dao instanceof ExampleDao_Impl);
        assertEquals(1, dao.ping());
    }

    private static DataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:lumen_ctx;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("sa");
        return dataSource;
    }

    private static Dialect dialect() {
        return new LimitOffsetDialect("\"");
    }
}

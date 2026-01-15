package io.lighting.lumen.example.todo.config;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.db.SqlLog;
import io.lighting.lumen.example.todo.repo.TodoQueryDao;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LumenSqlLogProperties.class)
public class LumenConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(LumenConfiguration.class);

    @Bean
    public Lumen lumen(DataSource dataSource, LumenSqlLogProperties sqlLogProperties) {
        SqlLog sqlLog = null;
        if (sqlLogProperties.isEnabled()) {
            sqlLog = SqlLog.builder()
                .mode(sqlLogProperties.getMode())
                .logOnRender(sqlLogProperties.isLogOnRender())
                .logOnExecute(sqlLogProperties.isLogOnExecute())
                .includeElapsed(sqlLogProperties.isIncludeElapsed())
                .includeRowCount(sqlLogProperties.isIncludeRowCount())
                .includeOperation(sqlLogProperties.isIncludeOperation())
                .prefix(sqlLogProperties.getPrefix())
                .sink(LOGGER::info)
                .build();
        }
        return Lumen.builder()
            .dataSource(dataSource)
            .observers(sqlLog == null ? java.util.List.of() : java.util.List.of(sqlLog))
            .build();
    }

    @Bean
    public Db db(Lumen lumen) {
        return lumen.db();
    }

    @Bean
    public TodoQueryDao todoQueryDao(Lumen lumen) {
        return lumen.dao(TodoQueryDao.class);
    }
}

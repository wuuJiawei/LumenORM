package io.lighting.lumen.example.todo.config;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.db.SqlLog;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@LumenDaoScan(basePackages = "io.lighting.lumen.example.todo.repo")
@EnableConfigurationProperties(LumenSqlLogProperties.class)
public class LumenConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(LumenConfiguration.class);

    @Bean
    public Lumen lumen(DataSource dataSource, LumenSqlLogProperties sqlLogProperties) {
        SqlLog sqlLog = SqlLog.builder()
                .mode(sqlLogProperties.getMode())
                .enabled(sqlLogProperties.isEnabled())
                .logOnRender(sqlLogProperties.isLogOnRender())
                .logOnExecute(sqlLogProperties.isLogOnExecute())
                .includeElapsed(sqlLogProperties.isIncludeElapsed())
                .includeRowCount(sqlLogProperties.isIncludeRowCount())
                .includeOperation(sqlLogProperties.isIncludeOperation())
                .prefix(sqlLogProperties.getPrefix())
                .sink(LOGGER::info)
                .build();
        return Lumen.builder()
            .dataSource(dataSource)
            .observers(List.of(sqlLog))
            .build();
    }

    @Bean
    public Db db(Lumen lumen) {
        return lumen.db();
    }

}

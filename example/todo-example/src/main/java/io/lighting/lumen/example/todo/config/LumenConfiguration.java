package io.lighting.lumen.example.todo.config;

import io.lighting.lumen.Lumen;
import io.lighting.lumen.db.Db;
import io.lighting.lumen.example.todo.repo.TodoQueryDao;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LumenConfiguration {
    @Bean
    public Lumen lumen(DataSource dataSource) {
        return Lumen.builder()
            .dataSource(dataSource)
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

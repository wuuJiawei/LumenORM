package io.lighting.lumen.example.todo.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(LumenDaoRegistrar.class)
public @interface LumenDaoScan {
    String[] basePackages() default {};
}

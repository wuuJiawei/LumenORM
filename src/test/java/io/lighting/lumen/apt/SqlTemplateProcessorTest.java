package io.lighting.lumen.apt;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class SqlTemplateProcessorTest {

    @Test
    void generatesTemplateClassForAnnotatedMethods() throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "JavaCompiler must be available");
        Path outputDir = Files.createTempDirectory("lumen-apt");
        String source = """
            package example;

            import io.lighting.lumen.annotations.SqlTemplate;

            public interface OrderRepo {
                @SqlTemplate("SELECT 1")
                void ping();
            }
            """;
        JavaFileObject fileObject = new StringJavaFileObject("example.OrderRepo", source);
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, List.of(outputDir.toFile()));
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outputDir.toFile()));
            List<String> options = List.of(
                "-proc:only",
                "-classpath",
                System.getProperty("java.class.path"),
                "-source",
                "21"
            );
            JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                fileManager,
                null,
                options,
                null,
                List.of(fileObject)
            );
            task.setProcessors(List.of(new SqlTemplateProcessor()));
            Boolean result = task.call();
            assertTrue(result);
        }

        Path generated = outputDir.resolve("example").resolve("OrderRepo_SqlTemplates.java");
        assertTrue(Files.exists(generated));
        String content = Files.readString(generated);
        assertTrue(content.contains("SELECT 1"));
        assertTrue(content.contains("PING_TEMPLATE"));
    }

    private static final class StringJavaFileObject extends SimpleJavaFileObject {
        private final String content;

        private StringJavaFileObject(String name, String content) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
        }
    }
}

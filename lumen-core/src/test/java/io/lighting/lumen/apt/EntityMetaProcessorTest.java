package io.lighting.lumen.apt;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class EntityMetaProcessorTest {

    @Test
    void generatesQClassForTableEntities() throws IOException {
        String source = """
            package example;

            import io.lighting.lumen.meta.Column;
            import io.lighting.lumen.meta.Id;
            import io.lighting.lumen.meta.Table;

            @Table(name = "orders")
            public final class Order {
                @Id
                private Long id;

                @Column(name = "order_no")
                private String orderNo;
            }
            """;
        CompilationResult result = compile("example.Order", source);
        assertTrue(result.success());

        Path generated = result.outputDir().resolve("example").resolve("meta").resolve("OrderMeta.java");
        assertTrue(Files.exists(generated));
        String content = Files.readString(generated);
        assertTrue(content.contains("new OrderMetaTable(\"orders\", \"o\")"));
        assertTrue(content.contains("ColumnRef.of(alias, \"id\")"));
        assertTrue(content.contains("ColumnRef.of(alias, \"order_no\")"));
    }

    private CompilationResult compile(String name, String source) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "JavaCompiler must be available");
        Path outputDir = Files.createTempDirectory("lumen-apt");
        JavaFileObject fileObject = new StringJavaFileObject(name, source);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
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
                diagnostics,
                options,
                null,
                List.of(fileObject)
            );
            task.setProcessors(List.of(new EntityMetaProcessor()));
            Boolean success = task.call();
            return new CompilationResult(outputDir, success, diagnostics.getDiagnostics());
        }
    }

    private record CompilationResult(Path outputDir, boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
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

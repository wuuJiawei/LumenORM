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

class SqlTemplateProcessorTest {

    @Test
    void generatesTemplateClassForAnnotatedMethods() throws IOException {
        String source = """
            package example;

            import io.lighting.lumen.template.annotations.SqlTemplate;

            public interface OrderRepo {
                @SqlTemplate("SELECT 1")
                void ping() throws java.sql.SQLException;
            }
            """;
        CompilationResult result = compile("example.OrderRepo", source);
        assertTrue(result.success());

        Path generated = result.outputDir().resolve("example").resolve("OrderRepo_SqlTemplates.java");
        assertTrue(Files.exists(generated));
        String content = Files.readString(generated);
        assertTrue(content.contains("SELECT 1"));
        assertTrue(content.contains("PING_TEMPLATE"));

        Path impl = result.outputDir().resolve("example").resolve("OrderRepo_Impl.java");
        assertTrue(Files.exists(impl));
        String implContent = Files.readString(impl);
        assertTrue(implContent.contains("class OrderRepo_Impl"));
    }

    @Test
    void validatesSqlConstFields() throws IOException {
        String source = """
            package example;

            import io.lighting.lumen.template.annotations.SqlConst;

            public final class Consts {
                @SqlConst
                public static final String Q = "SELECT 1";
            }
            """;
        CompilationResult result = compile("example.Consts", source);
        assertTrue(result.success());
    }

    @Test
    void rejectsNonConstantSqlConstFields() throws IOException {
        String source = """
            package example;

            import io.lighting.lumen.template.annotations.SqlConst;

            public final class Consts {
                @SqlConst
                public final String Q = "SELECT 1";
            }
            """;
        CompilationResult result = compile("example.Consts", source);
        assertTrue(!result.success());
        assertTrue(result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.getMessage(null).contains("static final field")));
    }

    @Test
    void rejectsMissingBindings() throws IOException {
        String source = """
            package example;

            import io.lighting.lumen.template.annotations.SqlTemplate;

            public interface OrderRepo {
                @SqlTemplate("SELECT * FROM orders WHERE id = :id")
                void load(String name) throws java.sql.SQLException;
            }
            """;
        CompilationResult result = compile("example.OrderRepo", source);
        assertTrue(!result.success());
        assertTrue(result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.getMessage(null).contains("Missing template bindings")));
    }

    @Test
    void rejectsOrderByParamsInAllowedFragments() throws IOException {
        String source = """
            package example;

            import io.lighting.lumen.template.annotations.SqlTemplate;

            public interface OrderRepo {
                @SqlTemplate("@orderBy(:sort, allowed = { BAD : :bad })")
                void load(String sort, String bad) throws java.sql.SQLException;
            }
            """;
        CompilationResult result = compile("example.OrderRepo", source);
        assertTrue(!result.success());
        assertTrue(result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.getMessage(null).contains("@orderBy fragments must not use parameters")));
    }

    @Test
    void requiresRowMapperForListReturn() throws IOException {
        String source = """
            package example;

            import io.lighting.lumen.template.annotations.SqlTemplate;
            import java.util.List;

            public interface OrderRepo {
                @SqlTemplate("SELECT 1")
                List<String> load() throws java.sql.SQLException;
            }
            """;
        CompilationResult result = compile("example.OrderRepo", source);
        assertTrue(!result.success());
        assertTrue(result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.getMessage(null).contains("RowMapper parameter")));
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
            task.setProcessors(List.of(new SqlTemplateProcessor()));
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

package io.lighting.lumen.compiler;

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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Tests for the compile-time SQL validation plugin.
 * These tests require the plugin JAR to be available on the classpath,
 * which is not the case in unit test execution.
 * To test the plugin, run: mvn verify -pl lumen-core -DskipTests=false
 */
@Disabled("Requires plugin JAR on classpath. Run 'mvn verify' instead.")
class SqlRunPluginTest {

    @Test
    void allowsValidSqlLiteral() throws IOException {
        String source = """
            package example;

            public final class Repo {
                void run(String sql) {}

                void ok() {
                    run("SELECT 1");
                }
            }
            """;
        CompilationResult result = compile("example.Repo", source);
        assertTrue(result.success());
    }

    @Test
    void rejectsInvalidSqlLiteral() throws IOException {
        String source = """
            package example;

            public final class Repo {
                void run(String sql) {}

                void bad() {
                    run("@if(");
                }
            }
            """;
        CompilationResult result = compile("example.Repo", source);
        assertTrue(!result.success());
        assertTrue(result.diagnostics().stream()
            .anyMatch(diagnostic -> diagnostic.getMessage(null).contains("Invalid SQL template")));
    }

    private CompilationResult compile(String name, String source) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue(compiler != null, "JavaCompiler must be available");
        Path outputDir = Files.createTempDirectory("lumen-plugin");
        JavaFileObject fileObject = new StringJavaFileObject(name, source);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(outputDir.toFile()));
            List<String> options = List.of(
                "-proc:none",
                "-classpath",
                System.getProperty("java.class.path"),
                "-Xplugin:lumen-sql-run",
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
            Boolean success = task.call();
            return new CompilationResult(success, diagnostics.getDiagnostics());
        }
    }

    private record CompilationResult(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
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

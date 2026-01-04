package io.lighting.lumen.compiler;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import io.lighting.lumen.template.SqlTemplate;
import javax.lang.model.element.ExecutableElement;
import javax.tools.Diagnostic;

public final class SqlRunPlugin implements Plugin {
    @Override
    public String getName() {
        return "lumen-sql-run";
    }

    @Override
    public void init(JavacTask task, String... args) {
        Trees trees = Trees.instance(task);
        task.addTaskListener(new TaskListener() {
            @Override
            public void finished(TaskEvent event) {
                if (event.getKind() != TaskEvent.Kind.ANALYZE) {
                    return;
                }
                CompilationUnitTree unit = event.getCompilationUnit();
                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
                        ExecutableElement element = toExecutable(trees.getElement(getCurrentPath()));
                        if (element == null || !element.getSimpleName().contentEquals("run")) {
                            return super.visitMethodInvocation(tree, unused);
                        }
                        if (element.getParameters().isEmpty()) {
                            return super.visitMethodInvocation(tree, unused);
                        }
                        if (!element.getParameters().get(0).asType().toString().equals(String.class.getName())) {
                            return super.visitMethodInvocation(tree, unused);
                        }
                        if (tree.getArguments().isEmpty()) {
                            return super.visitMethodInvocation(tree, unused);
                        }
                        ExpressionTree firstArg = tree.getArguments().get(0);
                        if (firstArg instanceof LiteralTree literal && literal.getValue() instanceof String sql) {
                            try {
                                SqlTemplate.parse(sql);
                            } catch (RuntimeException ex) {
                                trees.printMessage(
                                    Diagnostic.Kind.ERROR,
                                    "Invalid SQL template: " + ex.getMessage(),
                                    tree,
                                    unit
                                );
                            }
                        }
                        return super.visitMethodInvocation(tree, unused);
                    }
                }.scan(unit, null);
            }
        });
    }

    private ExecutableElement toExecutable(javax.lang.model.element.Element element) {
        if (element instanceof ExecutableElement executable) {
            return executable;
        }
        return null;
    }
}

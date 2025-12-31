package io.lighting.lumen.template;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SqlTemplateAnalyzer {
    private SqlTemplateAnalyzer() {
    }

    public static SqlTemplateAnalysis analyze(String template) {
        Objects.requireNonNull(template, "template");
        SqlTemplate parsed = SqlTemplate.parse(template);
        return analyze(parsed);
    }

    static SqlTemplateAnalysis analyze(SqlTemplate template) {
        Set<String> bindings = new LinkedHashSet<>();
        AnalysisState state = new AnalysisState();
        analyzeNodes(template.nodes(), Set.of(), bindings, state, false);
        return new SqlTemplateAnalysis(bindings, state.orderByHasParams);
    }

    private static void analyzeNodes(
        List<TemplateNode> nodes,
        Set<String> locals,
        Set<String> bindings,
        AnalysisState state,
        boolean inOrderByAllowed
    ) {
        for (TemplateNode node : nodes) {
            if (node instanceof ParamNode paramNode) {
                if (inOrderByAllowed) {
                    state.orderByHasParams = true;
                }
                collectExpression(paramNode.expression(), locals, bindings);
            } else if (node instanceof IfNode ifNode) {
                collectExpression(ifNode.condition(), locals, bindings);
                analyzeNodes(ifNode.body(), locals, bindings, state, inOrderByAllowed);
            } else if (node instanceof ForNode forNode) {
                collectExpression(forNode.source(), locals, bindings);
                Set<String> nextLocals = new LinkedHashSet<>(locals);
                nextLocals.add(forNode.variable());
                analyzeNodes(forNode.body(), nextLocals, bindings, state, inOrderByAllowed);
            } else if (node instanceof ClauseNode clauseNode) {
                analyzeNodes(clauseNode.body(), locals, bindings, state, inOrderByAllowed);
            } else if (node instanceof OrNode orNode) {
                analyzeNodes(orNode.body(), locals, bindings, state, inOrderByAllowed);
            } else if (node instanceof InNode inNode) {
                collectExpression(inNode.source(), locals, bindings);
            } else if (node instanceof PageNode pageNode) {
                collectExpression(pageNode.page(), locals, bindings);
                collectExpression(pageNode.pageSize(), locals, bindings);
            } else if (node instanceof OrderByNode orderByNode) {
                collectExpression(orderByNode.selection(), locals, bindings);
                for (List<TemplateNode> allowedNodes : orderByNode.allowed().values()) {
                    analyzeNodes(allowedNodes, locals, bindings, state, true);
                }
            }
        }
    }

    private static void collectExpression(
        TemplateExpression expression,
        Set<String> locals,
        Set<String> bindings
    ) {
        if (expression instanceof PathExpression pathExpression) {
            List<PathSegment> segments = pathExpression.segments();
            if (!segments.isEmpty()) {
                String root = segments.get(0).name();
                if (!locals.contains(root) && !TemplateContext.SYSTEM_DIALECT_KEY.equals(root)) {
                    bindings.add(root);
                }
            }
        } else if (expression instanceof UnaryExpression unaryExpression) {
            collectExpression(unaryExpression.expression(), locals, bindings);
        } else if (expression instanceof BinaryExpression binaryExpression) {
            collectExpression(binaryExpression.left(), locals, bindings);
            collectExpression(binaryExpression.right(), locals, bindings);
        }
    }

    private static final class AnalysisState {
        private boolean orderByHasParams;
    }
}

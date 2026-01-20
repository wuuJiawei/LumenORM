package io.lighting.lumen.template;

import java.util.List;
import java.util.Objects;

public final class SqlTemplatePages {
    private SqlTemplatePages() {
    }

    public static SqlTemplatePageExpression find(SqlTemplate template) {
        Objects.requireNonNull(template, "template");
        return findPageExpression(template.nodes());
    }

    private static SqlTemplatePageExpression findPageExpression(List<TemplateNode> nodes) {
        for (TemplateNode node : nodes) {
            if (node instanceof PageNode pageNode) {
                return new SqlTemplatePageExpression(pageNode.page(), pageNode.pageSize());
            }
            SqlTemplatePageExpression nested = null;
            if (node instanceof IfNode ifNode) {
                nested = findPageExpression(ifNode.body());
            } else if (node instanceof ForNode forNode) {
                nested = findPageExpression(forNode.body());
            } else if (node instanceof ClauseNode clauseNode) {
                nested = findPageExpression(clauseNode.body());
            } else if (node instanceof OrNode orNode) {
                nested = findPageExpression(orNode.body());
            }
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }
}

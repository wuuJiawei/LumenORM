package io.lighting.lumen.template;

import java.util.List;
import java.util.Objects;

sealed interface TemplateNode permits TextNode, ParamNode, IfNode, ForNode, ClauseNode,
    OrNode, InNode, TableNode, ColumnNode, PageNode {
}

record TextNode(String text) implements TemplateNode {
    TextNode {
        Objects.requireNonNull(text, "text");
    }
}

record ParamNode(TemplateExpression expression) implements TemplateNode {
    ParamNode {
        Objects.requireNonNull(expression, "expression");
    }
}

record IfNode(TemplateExpression condition, List<TemplateNode> body) implements TemplateNode {
    IfNode {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(body, "body");
    }
}

record ForNode(String variable, TemplateExpression source, List<TemplateNode> body) implements TemplateNode {
    ForNode {
        Objects.requireNonNull(variable, "variable");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(body, "body");
    }
}

record ClauseNode(String keyword, List<TemplateNode> body) implements TemplateNode {
    ClauseNode {
        Objects.requireNonNull(keyword, "keyword");
        Objects.requireNonNull(body, "body");
    }
}

record OrNode(List<TemplateNode> body) implements TemplateNode {
    OrNode {
        Objects.requireNonNull(body, "body");
    }
}

record InNode(TemplateExpression source) implements TemplateNode {
    InNode {
        Objects.requireNonNull(source, "source");
    }
}

record TableNode(String entityName) implements TemplateNode {
    TableNode {
        Objects.requireNonNull(entityName, "entityName");
    }
}

record ColumnNode(String entityName, String fieldName) implements TemplateNode {
    ColumnNode {
        Objects.requireNonNull(entityName, "entityName");
        Objects.requireNonNull(fieldName, "fieldName");
    }
}

record PageNode(TemplateExpression page, TemplateExpression pageSize) implements TemplateNode {
    PageNode {
        Objects.requireNonNull(page, "page");
        Objects.requireNonNull(pageSize, "pageSize");
    }
}

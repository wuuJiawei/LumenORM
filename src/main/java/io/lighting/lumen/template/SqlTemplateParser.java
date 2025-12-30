package io.lighting.lumen.template;

import java.util.ArrayList;
import java.util.List;

final class SqlTemplateParser {
    private final String input;
    private int index;

    SqlTemplateParser(String input) {
        this.input = input;
    }

    SqlTemplate parse() {
        List<TemplateNode> nodes = parseNodes('\0');
        return new SqlTemplate(nodes);
    }

    private List<TemplateNode> parseNodes(char terminator) {
        List<TemplateNode> nodes = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        while (!isAtEnd()) {
            char ch = peek();
            if (terminator != '\0' && ch == terminator) {
                flushText(text, nodes);
                index++;
                return nodes;
            }
            if (ch == '@' && isDirectiveStart()) {
                flushText(text, nodes);
                nodes.add(parseDirective());
                continue;
            }
            if (ch == ':' && isParamStart()) {
                flushText(text, nodes);
                nodes.add(parseParam());
                continue;
            }
            text.append(ch);
            index++;
        }
        if (terminator != '\0') {
            throw new IllegalArgumentException("Expected '" + terminator + "' before end of template");
        }
        flushText(text, nodes);
        return nodes;
    }

    private void flushText(StringBuilder text, List<TemplateNode> nodes) {
        if (text.length() > 0) {
            nodes.add(new TextNode(text.toString()));
            text.setLength(0);
        }
    }

    private TemplateNode parseDirective() {
        expect('@');
        String name = parseIdentifier();
        return switch (name) {
            case "if" -> parseIf();
            case "for" -> parseFor();
            case "where" -> parseClause("WHERE");
            case "having" -> parseClause("HAVING");
            case "or" -> parseOr();
            case "in" -> parseIn();
            case "table" -> parseTable();
            case "col" -> parseColumn();
            case "page" -> parsePage();
            case "fn" -> parseFn();
            default -> throw new IllegalArgumentException("Unknown directive @" + name);
        };
    }

    private TemplateNode parseIf() {
        String condition = parseParenContents();
        List<TemplateNode> body = parseBlock();
        return new IfNode(parseExpression(condition), body);
    }

    private TemplateNode parseFor() {
        String contents = parseParenContents();
        int colonIndex = contents.indexOf(':');
        if (colonIndex < 0) {
            throw new IllegalArgumentException("Invalid @for syntax, expected 'var : expr': " + contents);
        }
        String var = contents.substring(0, colonIndex).trim();
        String expr = contents.substring(colonIndex + 1).trim();
        if (var.isEmpty() || expr.isEmpty()) {
            throw new IllegalArgumentException("Invalid @for syntax, expected 'var : expr': " + contents);
        }
        List<TemplateNode> body = parseBlock();
        return new ForNode(var, parseExpression(expr), body);
    }

    private TemplateNode parseClause(String keyword) {
        List<TemplateNode> body = parseBlock();
        return new ClauseNode(keyword, body);
    }

    private TemplateNode parseOr() {
        List<TemplateNode> body = parseBlock();
        return new OrNode(body);
    }

    private TemplateNode parseIn() {
        String contents = parseParenContents();
        return new InNode(parseExpression(contents));
    }

    private TemplateNode parseTable() {
        String contents = parseParenContents().trim();
        if (contents.isEmpty()) {
            throw new IllegalArgumentException("@table requires entity name");
        }
        return new TableNode(contents);
    }

    private TemplateNode parseColumn() {
        String contents = parseParenContents().trim();
        int sep = contents.indexOf("::");
        if (sep <= 0 || sep == contents.length() - 2) {
            throw new IllegalArgumentException("@col requires Entity::field format");
        }
        String entity = contents.substring(0, sep).trim();
        String field = contents.substring(sep + 2).trim();
        return new ColumnNode(entity, field);
    }

    private TemplateNode parsePage() {
        String contents = parseParenContents();
        int comma = findTopLevelComma(contents);
        if (comma <= 0 || comma == contents.length() - 1) {
            throw new IllegalArgumentException("@page requires (page, pageSize)");
        }
        String page = contents.substring(0, comma).trim();
        String pageSize = contents.substring(comma + 1).trim();
        return new PageNode(parseExpression(page), parseExpression(pageSize));
    }

    private TemplateNode parseFn() {
        skipWhitespace();
        expect('.');
        String functionName = parseIdentifier();
        String contents = parseParenContents();
        List<String> args = splitArgs(contents);
        List<List<TemplateNode>> nodes = new ArrayList<>();
        for (String arg : args) {
            if (!arg.isBlank()) {
                nodes.add(new SqlTemplateParser(arg).parse().nodes());
            } else {
                nodes.add(List.of());
            }
        }
        return new FnNode(functionName, nodes);
    }

    private TemplateNode parseParam() {
        expect(':');
        StringBuilder name = new StringBuilder();
        while (!isAtEnd()) {
            char ch = peek();
            if (isIdentifierPart(ch) || ch == '.') {
                name.append(ch);
                index++;
            } else {
                break;
            }
        }
        if (name.length() == 0) {
            throw new IllegalArgumentException("Expected parameter name at position " + index);
        }
        return new ParamNode(parseExpression(name.toString()));
    }

    private List<TemplateNode> parseBlock() {
        skipWhitespace();
        expect('{');
        return parseNodes('}');
    }

    private String parseParenContents() {
        skipWhitespace();
        expect('(');
        int depth = 1;
        int start = index;
        while (!isAtEnd() && depth > 0) {
            char ch = peek();
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            }
            if (depth > 0) {
                index++;
            }
        }
        if (depth != 0) {
            throw new IllegalArgumentException("Unclosed '(' in template");
        }
        String contents = input.substring(start, index);
        index++;
        return contents.trim();
    }

    private TemplateExpression parseExpression(String expr) {
        return new TemplateExpressionParser(expr).parse();
    }

    private List<String> splitArgs(String input) {
        List<String> parts = new ArrayList<>();
        if (input.isBlank()) {
            return parts;
        }
        int depth = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '\\') {
                current.append(ch);
                if (i + 1 < input.length()) {
                    current.append(input.charAt(i + 1));
                    i++;
                }
                continue;
            }
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle;
                current.append(ch);
                continue;
            }
            if (ch == '"' && !inSingle) {
                inDouble = !inDouble;
                current.append(ch);
                continue;
            }
            if (!inSingle && !inDouble) {
                if (ch == '(') {
                    depth++;
                } else if (ch == ')') {
                    depth = Math.max(0, depth - 1);
                } else if (ch == ',' && depth == 0) {
                    parts.add(current.toString().trim());
                    current.setLength(0);
                    continue;
                }
            }
            current.append(ch);
        }
        if (current.length() > 0) {
            parts.add(current.toString().trim());
        }
        return parts;
    }

    private int findTopLevelComma(String input) {
        int depth = 0;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private boolean isDirectiveStart() {
        return isIdentifierStart(peekNext());
    }

    private boolean isParamStart() {
        char next = peekNext();
        return isIdentifierStart(next);
    }

    private String parseIdentifier() {
        if (!isIdentifierStart(peek())) {
            throw new IllegalArgumentException("Expected identifier at position " + index);
        }
        int start = index;
        index++;
        while (!isAtEnd() && isIdentifierPart(peek())) {
            index++;
        }
        return input.substring(start, index);
    }

    private void skipWhitespace() {
        while (!isAtEnd() && Character.isWhitespace(peek())) {
            index++;
        }
    }

    private void expect(char ch) {
        if (peek() != ch) {
            throw new IllegalArgumentException("Expected '" + ch + "' at position " + index);
        }
        index++;
    }


    private char peek() {
        if (isAtEnd()) {
            return '\0';
        }
        return input.charAt(index);
    }

    private char peekNext() {
        if (index + 1 >= input.length()) {
            return '\0';
        }
        return input.charAt(index + 1);
    }

    private boolean isAtEnd() {
        return index >= input.length();
    }

    private boolean isIdentifierStart(char ch) {
        return Character.isLetter(ch) || ch == '_';
    }

    private boolean isIdentifierPart(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_';
    }
}

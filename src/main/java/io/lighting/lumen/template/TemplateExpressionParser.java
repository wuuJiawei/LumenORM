package io.lighting.lumen.template;

import java.util.ArrayList;
import java.util.List;

final class TemplateExpressionParser {
    private final String input;
    private int index;

    TemplateExpressionParser(String input) {
        this.input = input;
        this.index = 0;
    }

    TemplateExpression parse() {
        TemplateExpression expression = parseOr();
        skipWhitespace();
        if (!isAtEnd()) {
            throw new IllegalArgumentException("Unexpected token at position " + index + " in expression: " + input);
        }
        return expression;
    }

    private TemplateExpression parseOr() {
        TemplateExpression left = parseAnd();
        while (match("||")) {
            TemplateExpression right = parseAnd();
            left = new BinaryExpression(left, BinaryOp.OR, right);
        }
        return left;
    }

    private TemplateExpression parseAnd() {
        TemplateExpression left = parseEquality();
        while (match("&&")) {
            TemplateExpression right = parseEquality();
            left = new BinaryExpression(left, BinaryOp.AND, right);
        }
        return left;
    }

    private TemplateExpression parseEquality() {
        TemplateExpression left = parseRelational();
        while (true) {
            if (match("==")) {
                TemplateExpression right = parseRelational();
                left = new BinaryExpression(left, BinaryOp.EQ, right);
            } else if (match("!=")) {
                TemplateExpression right = parseRelational();
                left = new BinaryExpression(left, BinaryOp.NE, right);
            } else {
                return left;
            }
        }
    }

    private TemplateExpression parseRelational() {
        TemplateExpression left = parseUnary();
        while (true) {
            if (match("<=")) {
                TemplateExpression right = parseUnary();
                left = new BinaryExpression(left, BinaryOp.LE, right);
            } else if (match(">=")) {
                TemplateExpression right = parseUnary();
                left = new BinaryExpression(left, BinaryOp.GE, right);
            } else if (match("<")) {
                TemplateExpression right = parseUnary();
                left = new BinaryExpression(left, BinaryOp.LT, right);
            } else if (match(">")) {
                TemplateExpression right = parseUnary();
                left = new BinaryExpression(left, BinaryOp.GT, right);
            } else {
                return left;
            }
        }
    }

    private TemplateExpression parseUnary() {
        skipWhitespace();
        if (match("!")) {
            return new UnaryExpression(parseUnary());
        }
        return parsePrimary();
    }

    private TemplateExpression parsePrimary() {
        skipWhitespace();
        if (match("(")) {
            TemplateExpression inner = parseOr();
            expect(")");
            return inner;
        }
        if (peek() == '"' || peek() == '\'') {
            return new LiteralExpression(parseStringLiteral());
        }
        if (peek() == '-' || Character.isDigit(peek())) {
            return new LiteralExpression(parseNumber());
        }
        if (matchKeyword("null")) {
            return new LiteralExpression(null);
        }
        if (matchKeyword("true")) {
            return new LiteralExpression(true);
        }
        if (matchKeyword("false")) {
            return new LiteralExpression(false);
        }
        if (peek() == ':' && isIdentifierStart(peekNext())) {
            index++;
        }
        if (isIdentifierStart(peek())) {
            return parsePath();
        }
        throw new IllegalArgumentException("Unexpected token at position " + index + " in expression: " + input);
    }

    private TemplateExpression parsePath() {
        List<PathSegment> segments = new ArrayList<>();
        segments.add(parseSegment());
        while (match(".")) {
            segments.add(parseSegment());
        }
        return new PathExpression(segments);
    }

    private PathSegment parseSegment() {
        String name = parseIdentifier();
        skipWhitespace();
        if (match("(")) {
            expect(")");
            return new PathSegment(name, true);
        }
        return new PathSegment(name, false);
    }

    private String parseIdentifier() {
        skipWhitespace();
        if (!isIdentifierStart(peek())) {
            throw new IllegalArgumentException("Expected identifier at position " + index + " in expression: " + input);
        }
        int start = index;
        index++;
        while (!isAtEnd() && isIdentifierPart(peek())) {
            index++;
        }
        return input.substring(start, index);
    }

    private String parseStringLiteral() {
        char quote = peek();
        index++;
        StringBuilder builder = new StringBuilder();
        while (!isAtEnd() && peek() != quote) {
            char ch = peek();
            if (ch == '\\') {
                index++;
                if (isAtEnd()) {
                    throw new IllegalArgumentException("Unterminated string literal in expression: " + input);
                }
                builder.append(peek());
            } else {
                builder.append(ch);
            }
            index++;
        }
        if (isAtEnd()) {
            throw new IllegalArgumentException("Unterminated string literal in expression: " + input);
        }
        index++;
        return builder.toString();
    }

    private Number parseNumber() {
        int start = index;
        if (peek() == '-') {
            index++;
        }
        while (!isAtEnd() && Character.isDigit(peek())) {
            index++;
        }
        String value = input.substring(start, index);
        return Long.parseLong(value);
    }

    private boolean matchKeyword(String keyword) {
        skipWhitespace();
        int length = keyword.length();
        if (index + length > input.length()) {
            return false;
        }
        String slice = input.substring(index, index + length);
        if (!slice.equals(keyword)) {
            return false;
        }
        int end = index + length;
        if (end < input.length() && isIdentifierPart(input.charAt(end))) {
            return false;
        }
        index = end;
        return true;
    }

    private void skipWhitespace() {
        while (!isAtEnd() && Character.isWhitespace(peek())) {
            index++;
        }
    }

    private boolean match(String token) {
        skipWhitespace();
        if (input.startsWith(token, index)) {
            index += token.length();
            return true;
        }
        return false;
    }

    private void expect(String token) {
        if (!match(token)) {
            throw new IllegalArgumentException("Expected '" + token + "' at position " + index + " in expression: " + input);
        }
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

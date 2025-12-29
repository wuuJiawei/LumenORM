package io.lighting.lumen.template;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

interface TemplateExpression {
    Object evaluate(TemplateContext context);

    static boolean toBoolean(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence sequence) {
            return sequence.length() > 0;
        }
        if (value instanceof Iterable<?> iterable) {
            return iterable.iterator().hasNext();
        }
        return true;
    }

    static int compareValues(Object left, Object right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("Cannot compare null values");
        }
        if (left instanceof Number leftNum && right instanceof Number rightNum) {
            BigDecimal leftDec = new BigDecimal(leftNum.toString());
            BigDecimal rightDec = new BigDecimal(rightNum.toString());
            return leftDec.compareTo(rightDec);
        }
        if (left instanceof Comparable<?> comparable && left.getClass().isInstance(right)) {
            @SuppressWarnings("unchecked")
            Comparable<Object> cast = (Comparable<Object>) comparable;
            return cast.compareTo(right);
        }
        throw new IllegalArgumentException(
            "Cannot compare values of type " + left.getClass().getSimpleName()
                + " and " + right.getClass().getSimpleName()
        );
    }
}

final class LiteralExpression implements TemplateExpression {
    private final Object value;

    LiteralExpression(Object value) {
        this.value = value;
    }

    @Override
    public Object evaluate(TemplateContext context) {
        return value;
    }
}

final class PathExpression implements TemplateExpression {
    private final List<PathSegment> segments;

    PathExpression(List<PathSegment> segments) {
        this.segments = List.copyOf(segments);
    }

    @Override
    public Object evaluate(TemplateContext context) {
        return context.resolvePath(segments);
    }
}

final class UnaryExpression implements TemplateExpression {
    private final TemplateExpression expression;

    UnaryExpression(TemplateExpression expression) {
        this.expression = Objects.requireNonNull(expression, "expression");
    }

    @Override
    public Object evaluate(TemplateContext context) {
        return !TemplateExpression.toBoolean(expression.evaluate(context));
    }
}

enum BinaryOp {
    OR,
    AND,
    EQ,
    NE,
    LT,
    LE,
    GT,
    GE
}

final class BinaryExpression implements TemplateExpression {
    private final TemplateExpression left;
    private final TemplateExpression right;
    private final BinaryOp op;

    BinaryExpression(TemplateExpression left, BinaryOp op, TemplateExpression right) {
        this.left = Objects.requireNonNull(left, "left");
        this.right = Objects.requireNonNull(right, "right");
        this.op = Objects.requireNonNull(op, "op");
    }

    @Override
    public Object evaluate(TemplateContext context) {
        return switch (op) {
            case OR -> TemplateExpression.toBoolean(left.evaluate(context))
                || TemplateExpression.toBoolean(right.evaluate(context));
            case AND -> TemplateExpression.toBoolean(left.evaluate(context))
                && TemplateExpression.toBoolean(right.evaluate(context));
            case EQ -> Objects.equals(left.evaluate(context), right.evaluate(context));
            case NE -> !Objects.equals(left.evaluate(context), right.evaluate(context));
            case LT -> TemplateExpression.compareValues(left.evaluate(context), right.evaluate(context)) < 0;
            case LE -> TemplateExpression.compareValues(left.evaluate(context), right.evaluate(context)) <= 0;
            case GT -> TemplateExpression.compareValues(left.evaluate(context), right.evaluate(context)) > 0;
            case GE -> TemplateExpression.compareValues(left.evaluate(context), right.evaluate(context)) >= 0;
        };
    }
}

record PathSegment(String name, boolean methodCall) {
    PathSegment {
        Objects.requireNonNull(name, "name");
    }
}

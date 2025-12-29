package io.lighting.lumen.sql.ast;

import java.util.List;
import java.util.Objects;

public sealed interface Expr permits
    Expr.And, Expr.Or, Expr.Not,
    Expr.Compare, Expr.In, Expr.Like,
    Expr.Func, Expr.Param, Expr.Column, Expr.Literal, Expr.RawSql, Expr.True, Expr.False {

    record And(List<Expr> items) implements Expr {
        public And {
            Objects.requireNonNull(items, "items");
            items = List.copyOf(items);
        }
    }

    record Or(List<Expr> items) implements Expr {
        public Or {
            Objects.requireNonNull(items, "items");
            items = List.copyOf(items);
        }
    }

    record Not(Expr item) implements Expr {
        public Not {
            Objects.requireNonNull(item, "item");
        }
    }

    record Compare(Expr left, Op op, Expr right) implements Expr {
        public Compare {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(op, "op");
            Objects.requireNonNull(right, "right");
        }
    }

    record In(Expr left, List<Expr> rights) implements Expr {
        public In {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(rights, "rights");
            rights = List.copyOf(rights);
        }
    }

    record Like(Expr left, Expr pattern) implements Expr {
        public Like {
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(pattern, "pattern");
        }
    }

    record Func(String name, List<Expr> args) implements Expr {
        public Func {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(args, "args");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
            args = List.copyOf(args);
        }
    }

    record Param(String name) implements Expr {
        public Param {
            Objects.requireNonNull(name, "name");
            if (name.isBlank()) {
                throw new IllegalArgumentException("name must not be blank");
            }
        }
    }

    record Column(String tableAlias, String columnName) implements Expr {
        public Column {
            Objects.requireNonNull(columnName, "columnName");
            if (columnName.isBlank()) {
                throw new IllegalArgumentException("columnName must not be blank");
            }
        }
    }

    record Literal(Object value) implements Expr {
    }

    record RawSql(String sqlFragment) implements Expr {
        public RawSql {
            Objects.requireNonNull(sqlFragment, "sqlFragment");
            if (sqlFragment.isBlank()) {
                throw new IllegalArgumentException("sqlFragment must not be blank");
            }
        }
    }

    record True() implements Expr {
    }

    record False() implements Expr {
    }

    enum Op { EQ, NE, GT, GE, LT, LE }
}

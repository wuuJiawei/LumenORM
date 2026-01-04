package io.lighting.lumen.sql;

import io.lighting.lumen.sql.ast.Expr;
import io.lighting.lumen.sql.ast.InsertStmt;
import io.lighting.lumen.sql.ast.Join;
import io.lighting.lumen.sql.ast.JoinType;
import io.lighting.lumen.sql.ast.OrderItem;
import io.lighting.lumen.sql.ast.Paging;
import io.lighting.lumen.sql.ast.SelectItem;
import io.lighting.lumen.sql.ast.SelectStmt;
import io.lighting.lumen.sql.ast.Stmt;
import io.lighting.lumen.sql.ast.TableRef;
import io.lighting.lumen.sql.ast.UpdateItem;
import io.lighting.lumen.sql.ast.UpdateStmt;
import io.lighting.lumen.sql.ast.DeleteStmt;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SqlRenderer {
    private final Dialect dialect;

    public SqlRenderer(Dialect dialect) {
        this.dialect = Objects.requireNonNull(dialect, "dialect");
    }

    public RenderedSql render(Stmt stmt, Bindings bindings) {
        Objects.requireNonNull(stmt, "stmt");
        Objects.requireNonNull(bindings, "bindings");
        StringBuilder sql = new StringBuilder();
        List<Bind> binds = new ArrayList<>();
        if (stmt instanceof SelectStmt selectStmt) {
            renderSelect(selectStmt, bindings, sql, binds);
        } else if (stmt instanceof InsertStmt insertStmt) {
            renderInsert(insertStmt, bindings, sql, binds);
        } else if (stmt instanceof UpdateStmt updateStmt) {
            renderUpdate(updateStmt, bindings, sql, binds);
        } else if (stmt instanceof DeleteStmt deleteStmt) {
            renderDelete(deleteStmt, bindings, sql, binds);
        } else {
            throw new IllegalArgumentException("Unsupported statement: " + stmt.getClass().getSimpleName());
        }
        return new RenderedSql(sql.toString(), binds);
    }

    private void renderSelect(SelectStmt stmt, Bindings bindings, StringBuilder sql, List<Bind> binds) {
        if (stmt.select().isEmpty()) {
            throw new IllegalArgumentException("Select list must not be empty");
        }
        sql.append("SELECT ");
        for (int i = 0; i < stmt.select().size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            renderSelectItem(stmt.select().get(i), bindings, sql, binds);
        }
        sql.append(" FROM ");
        renderTableRef(stmt.from(), sql);
        for (Join join : stmt.joins()) {
            sql.append(' ').append(joinKeyword(join.type())).append(' ');
            renderTableRef(join.table(), sql);
            sql.append(" ON ");
            renderExpr(join.on(), bindings, sql, binds);
        }
        if (stmt.where() != null) {
            sql.append(" WHERE ");
            renderExpr(stmt.where(), bindings, sql, binds);
        }
        if (!stmt.groupBy().isEmpty()) {
            sql.append(" GROUP BY ");
            for (int i = 0; i < stmt.groupBy().size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                renderExpr(stmt.groupBy().get(i), bindings, sql, binds);
            }
        }
        if (stmt.having() != null) {
            sql.append(" HAVING ");
            renderExpr(stmt.having(), bindings, sql, binds);
        }
        if (!stmt.orderBy().isEmpty()) {
            sql.append(" ORDER BY ");
            for (int i = 0; i < stmt.orderBy().size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                renderOrderItem(stmt.orderBy().get(i), bindings, sql, binds);
            }
        }
        Paging paging = stmt.paging();
        if (paging != null) {
            RenderedPagination pagination = dialect.renderPagination(
                paging.page(),
                paging.pageSize(),
                stmt.orderBy()
            );
            if (!pagination.sqlFragment().isBlank()) {
                sql.append(pagination.sqlFragment());
            }
            binds.addAll(pagination.binds());
        }
    }

    private void renderInsert(InsertStmt stmt, Bindings bindings, StringBuilder sql, List<Bind> binds) {
        sql.append("INSERT INTO ");
        renderTableRef(stmt.table(), sql);
        sql.append(" (");
        for (int i = 0; i < stmt.columns().size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            appendIdent(stmt.columns().get(i), sql);
        }
        sql.append(") VALUES ");
        for (int rowIndex = 0; rowIndex < stmt.rows().size(); rowIndex++) {
            if (rowIndex > 0) {
                sql.append(", ");
            }
            sql.append('(');
            List<Expr> row = stmt.rows().get(rowIndex);
            for (int colIndex = 0; colIndex < row.size(); colIndex++) {
                if (colIndex > 0) {
                    sql.append(", ");
                }
                renderExpr(row.get(colIndex), bindings, sql, binds);
            }
            sql.append(')');
        }
    }

    private void renderUpdate(UpdateStmt stmt, Bindings bindings, StringBuilder sql, List<Bind> binds) {
        sql.append("UPDATE ");
        renderTableRef(stmt.table(), sql);
        sql.append(" SET ");
        for (int i = 0; i < stmt.assignments().size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            renderUpdateItem(stmt.assignments().get(i), bindings, sql, binds);
        }
        if (stmt.where() != null) {
            sql.append(" WHERE ");
            renderExpr(stmt.where(), bindings, sql, binds);
        }
    }

    private void renderDelete(DeleteStmt stmt, Bindings bindings, StringBuilder sql, List<Bind> binds) {
        sql.append("DELETE FROM ");
        renderTableRef(stmt.table(), sql);
        if (stmt.where() != null) {
            sql.append(" WHERE ");
            renderExpr(stmt.where(), bindings, sql, binds);
        }
    }

    private void renderSelectItem(SelectItem item, Bindings bindings, StringBuilder sql, List<Bind> binds) {
        renderExpr(item.expr(), bindings, sql, binds);
        if (item.alias() != null && !item.alias().isBlank()) {
            sql.append(" AS ");
            appendIdent(item.alias(), sql);
        }
    }

    private void renderUpdateItem(UpdateItem item, Bindings bindings, StringBuilder sql, List<Bind> binds) {
        renderExpr(item.column(), bindings, sql, binds);
        sql.append(" = ");
        renderExpr(item.value(), bindings, sql, binds);
    }

    private void renderOrderItem(OrderItem item, Bindings bindings, StringBuilder sql, List<Bind> binds) {
        renderExpr(item.expr(), bindings, sql, binds);
        sql.append(item.asc() ? " ASC" : " DESC");
    }

    private void renderTableRef(TableRef ref, StringBuilder sql) {
        appendIdent(ref.tableName(), sql);
        if (ref.alias() != null && !ref.alias().isBlank()) {
            sql.append(' ');
            appendIdent(ref.alias(), sql);
        }
    }

    private void renderExpr(Expr expr, Bindings bindings, StringBuilder sql, List<Bind> binds) {
        if (expr instanceof Expr.And and) {
            renderLogical("AND", and.items(), bindings, sql, binds);
        } else if (expr instanceof Expr.Or or) {
            renderLogical("OR", or.items(), bindings, sql, binds);
        } else if (expr instanceof Expr.Not not) {
            sql.append("NOT ");
            renderWrapped(not.item(), bindings, sql, binds);
        } else if (expr instanceof Expr.Compare compare) {
            renderExpr(compare.left(), bindings, sql, binds);
            sql.append(' ').append(compareOp(compare.op())).append(' ');
            renderExpr(compare.right(), bindings, sql, binds);
        } else if (expr instanceof Expr.In in) {
            renderIn(in, bindings, sql, binds);
        } else if (expr instanceof Expr.Like like) {
            renderExpr(like.left(), bindings, sql, binds);
            sql.append(" LIKE ");
            renderExpr(like.pattern(), bindings, sql, binds);
        } else if (expr instanceof Expr.Func func) {
            List<RenderedSql> args = new ArrayList<>();
            for (Expr arg : func.args()) {
                StringBuilder argSql = new StringBuilder();
                List<Bind> argBinds = new ArrayList<>();
                renderExpr(arg, bindings, argSql, argBinds);
                args.add(new RenderedSql(argSql.toString(), argBinds));
            }
            RenderedSql rendered = dialect.renderFunction(func.name(), args);
            sql.append(rendered.sql());
            binds.addAll(rendered.binds());
        } else if (expr instanceof Expr.Param param) {
            Object value = bindings.require(param.name());
            sql.append('?');
            if (value instanceof Bind bind) {
                binds.add(bind);
            } else if (value == null) {
                binds.add(new Bind.NullValue(0));
            } else {
                binds.add(new Bind.Value(value, 0));
            }
        } else if (expr instanceof Expr.Column column) {
            if (column.tableAlias() != null && !column.tableAlias().isBlank()) {
                appendIdent(column.tableAlias(), sql);
                sql.append('.');
            }
            appendIdent(column.columnName(), sql);
        } else if (expr instanceof Expr.Literal literal) {
            sql.append('?');
            if (literal.value() == null) {
                binds.add(new Bind.NullValue(0));
            } else {
                binds.add(new Bind.Value(literal.value(), 0));
            }
        } else if (expr instanceof Expr.RawSql rawSql) {
            sql.append(rawSql.sqlFragment());
        } else if (expr instanceof Expr.True) {
            sql.append("1=1");
        } else if (expr instanceof Expr.False) {
            sql.append("1=0");
        } else {
            throw new IllegalArgumentException("Unsupported expression: " + expr.getClass().getSimpleName());
        }
    }

    private void renderLogical(String op, List<Expr> items, Bindings bindings, StringBuilder sql, List<Bind> binds) {
        if (items.isEmpty()) {
            sql.append(op.equals("AND") ? "1=1" : "1=0");
            return;
        }
        if (items.size() == 1) {
            renderExpr(items.get(0), bindings, sql, binds);
            return;
        }
        sql.append('(');
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sql.append(' ').append(op).append(' ');
            }
            renderExpr(items.get(i), bindings, sql, binds);
        }
        sql.append(')');
    }

    private void renderIn(Expr.In in, Bindings bindings, StringBuilder sql, List<Bind> binds) {
        if (in.rights().isEmpty()) {
            sql.append("1=0");
            return;
        }
        renderExpr(in.left(), bindings, sql, binds);
        sql.append(" IN (");
        for (int i = 0; i < in.rights().size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            renderExpr(in.rights().get(i), bindings, sql, binds);
        }
        sql.append(')');
    }

    private void renderWrapped(Expr expr, Bindings bindings, StringBuilder sql, List<Bind> binds) {
        sql.append('(');
        renderExpr(expr, bindings, sql, binds);
        sql.append(')');
    }

    private void appendIdent(String ident, StringBuilder sql) {
        String[] parts = ident.split("\\.");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sql.append('.');
            }
            sql.append(dialect.quoteIdent(parts[i]));
        }
    }

    private String joinKeyword(JoinType type) {
        return switch (type) {
            case JOIN -> "JOIN";
            case LEFT_JOIN -> "LEFT JOIN";
            case RIGHT_JOIN -> "RIGHT JOIN";
        };
    }

    private String compareOp(Expr.Op op) {
        return switch (op) {
            case EQ -> "=";
            case NE -> "<>";
            case GT -> ">";
            case GE -> ">=";
            case LT -> "<";
            case LE -> "<=";
        };
    }
}

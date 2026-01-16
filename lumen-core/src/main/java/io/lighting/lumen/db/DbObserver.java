package io.lighting.lumen.db;

import io.lighting.lumen.sql.RenderedSql;

/**
 * 数据库操作观察器。
 * <p>
 * 该接口用于监听 SQL 的渲染与执行生命周期，便于实现日志、指标、审计等横切能力。
 * 各回调方法均提供默认空实现，按需覆盖即可。
 * <p>
 * 生命周期顺序（正常情况下）：
 * <ol>
 *   <li>{@link #beforeRender}</li>
 *   <li>{@link #afterRender}</li>
 *   <li>{@link #beforeExecute}</li>
 *   <li>{@link #afterExecute}</li>
 * </ol>
 * 若出现异常，则会触发对应的 {@link #onRenderError} 或 {@link #onExecuteError} 回调。
 * <p>
 * 线程模型由调用方决定；实现类若持有状态，应自行保证线程安全。
 */
public interface DbObserver {
    /**
     * SQL 渲染前回调。
     *
     * @param operation 当前操作类型（例如 SELECT/UPDATE）
     * @param source    操作来源（可能是 DSL/模板/Query/Command 等）
     */
    default void beforeRender(DbOperation operation, Object source) {
    }

    /**
     * SQL 渲染后回调。
     *
     * @param operation    当前操作类型
     * @param source       操作来源
     * @param rendered     渲染后的 SQL 与绑定参数
     * @param elapsedNanos 渲染耗时（纳秒）
     */
    default void afterRender(DbOperation operation, Object source, RenderedSql rendered, long elapsedNanos) {
    }

    /**
     * SQL 渲染失败回调。
     *
     * @param operation    当前操作类型
     * @param source       操作来源
     * @param error        渲染异常
     * @param elapsedNanos 渲染耗时（纳秒）
     */
    default void onRenderError(DbOperation operation, Object source, Exception error, long elapsedNanos) {
    }

    /**
     * SQL 执行前回调。
     *
     * @param operation 当前操作类型
     * @param source    操作来源
     * @param rendered  即将执行的 SQL 与绑定参数
     */
    default void beforeExecute(DbOperation operation, Object source, RenderedSql rendered) {
    }

    /**
     * SQL 执行后回调。
     *
     * @param operation    当前操作类型
     * @param source       操作来源
     * @param rendered     已执行的 SQL 与绑定参数
     * @param elapsedNanos 执行耗时（纳秒）
     * @param rowCount     影响行数（查询操作可能为 0 或数据库返回值）
     */
    default void afterExecute(
        DbOperation operation,
        Object source,
        RenderedSql rendered,
        long elapsedNanos,
        int rowCount
    ) {
    }

    /**
     * SQL 执行失败回调。
     *
     * @param operation    当前操作类型
     * @param source       操作来源
     * @param rendered     已生成的 SQL 与绑定参数
     * @param elapsedNanos 执行耗时（纳秒）
     * @param error        执行异常
     */
    default void onExecuteError(
        DbOperation operation,
        Object source,
        RenderedSql rendered,
        long elapsedNanos,
        Exception error
    ) {
    }
}

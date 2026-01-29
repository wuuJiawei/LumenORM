package io.lighting.lumen.starter;

import io.lighting.lumen.db.SqlLog;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for LumenORM.
 * <p>
 * Configure these properties under the "lumen" prefix in application.yml:
 * <pre>{@code
 * lumen:
 *   sql:
 *     log:
 *       enabled: true
 *       mode: SEPARATE
 *       include-elapsed: true
 * }</pre>
 */
@ConfigurationProperties(prefix = "lumen")
public class LumenProperties {

    private SqlLogProperties sql = new SqlLogProperties();

    public SqlLogProperties getSql() {
        return sql;
    }

    public void setSql(SqlLogProperties sql) {
        this.sql = sql;
    }

    public static class SqlLogProperties {
        private boolean enabled = true;
        private boolean logOnRender = false;
        private boolean logOnExecute = true;
        private boolean includeElapsed = false;
        private boolean includeRowCount = false;
        private boolean includeOperation = true;
        private SqlLog.Mode mode = SqlLog.Mode.SEPARATE;
        private String prefix = "SQL:";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isLogOnRender() {
            return logOnRender;
        }

        public void setLogOnRender(boolean logOnRender) {
            this.logOnRender = logOnRender;
        }

        public boolean isLogOnExecute() {
            return logOnExecute;
        }

        public void setLogOnExecute(boolean logOnExecute) {
            this.logOnExecute = logOnExecute;
        }

        public boolean isIncludeElapsed() {
            return includeElapsed;
        }

        public void setIncludeElapsed(boolean includeElapsed) {
            this.includeElapsed = includeElapsed;
        }

        public boolean isIncludeRowCount() {
            return includeRowCount;
        }

        public void setIncludeRowCount(boolean includeRowCount) {
            this.includeRowCount = includeRowCount;
        }

        public boolean isIncludeOperation() {
            return includeOperation;
        }

        public void setIncludeOperation(boolean includeOperation) {
            this.includeOperation = includeOperation;
        }

        public SqlLog.Mode getMode() {
            return mode;
        }

        public void setMode(SqlLog.Mode mode) {
            this.mode = mode;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public SqlLog build(java.util.function.Consumer<String> logger) {
            if (!enabled) {
                return null;
            }
            return SqlLog.builder()
                .mode(mode)
                .logOnRender(logOnRender)
                .logOnExecute(logOnExecute)
                .includeElapsed(includeElapsed)
                .includeRowCount(includeRowCount)
                .includeOperation(includeOperation)
                .prefix(prefix)
                .sink(logger)
                .build();
        }
    }
}

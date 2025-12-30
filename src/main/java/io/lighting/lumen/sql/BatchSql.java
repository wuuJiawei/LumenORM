package io.lighting.lumen.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BatchSql {
    private final RenderedSql template;
    private final List<List<Bind>> batches;
    private final int batchSize;

    public BatchSql(RenderedSql template, List<List<Bind>> batches, int batchSize) {
        this.template = Objects.requireNonNull(template, "template");
        this.batches = List.copyOf(Objects.requireNonNull(batches, "batches"));
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1");
        }
        this.batchSize = batchSize;
        for (List<Bind> binds : batches) {
            Objects.requireNonNull(binds, "binds");
        }
    }

    public RenderedSql template() {
        return template;
    }

    public List<List<Bind>> batches() {
        return batches;
    }

    public int batchSize() {
        return batchSize;
    }

    public int totalBatches() {
        return batches.size();
    }

    public static Builder builder(RenderedSql template) {
        return new Builder(template);
    }

    public static final class Builder {
        private final RenderedSql template;
        private final List<List<Bind>> batches = new ArrayList<>();
        private int batchSize = 1000;

        private Builder(RenderedSql template) {
            this.template = Objects.requireNonNull(template, "template");
        }

        public Builder add(List<Bind> binds) {
            batches.add(List.copyOf(Objects.requireNonNull(binds, "binds")));
            return this;
        }

        public Builder batchSize(int size) {
            if (size < 1) {
                throw new IllegalArgumentException("batchSize must be >= 1");
            }
            this.batchSize = size;
            return this;
        }

        public BatchSql build() {
            if (batches.isEmpty()) {
                throw new IllegalArgumentException("Batch binds must not be empty");
            }
            return new BatchSql(template, batches, batchSize);
        }
    }
}

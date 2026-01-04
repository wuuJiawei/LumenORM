package io.lighting.lumen.template;

import java.util.Set;

public final class SqlTemplateAnalysis {
    private final Set<String> bindings;
    private final boolean orderByHasParams;

    SqlTemplateAnalysis(Set<String> bindings, boolean orderByHasParams) {
        this.bindings = Set.copyOf(bindings);
        this.orderByHasParams = orderByHasParams;
    }

    public Set<String> bindings() {
        return bindings;
    }

    public boolean orderByHasParams() {
        return orderByHasParams;
    }
}

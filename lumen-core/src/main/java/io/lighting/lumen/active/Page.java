package io.lighting.lumen.active;

public record Page(int page, int pageSize) {
    public static Page of(int page, int pageSize) {
        return new Page(page, pageSize);
    }
}

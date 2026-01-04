package io.lighting.lumen.example.dao;

import java.util.List;

public record OrderFilter(List<Long> ids, String status) {
}

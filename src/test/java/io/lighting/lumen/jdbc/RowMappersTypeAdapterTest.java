package io.lighting.lumen.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Type;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.h2.tools.SimpleResultSet;
import org.junit.jupiter.api.Test;

class RowMappersTypeAdapterTest {
    @Test
    void mapsGenericCollectionsAndCommonTypes() throws SQLException {
        SimpleResultSet resultSet = new SimpleResultSet();
        resultSet.addColumn("IDS", Types.ARRAY, 0, 0);
        resultSet.addColumn("TAGS", Types.ARRAY, 0, 0);
        resultSet.addColumn("ATTRS", Types.OTHER, 0, 0);
        resultSet.addColumn("STATUS", Types.VARCHAR, 0, 0);
        resultSet.addColumn("TOKEN", Types.VARCHAR, 0, 0);
        UUID token = UUID.randomUUID();
        resultSet.addRow(
            new Object[] { new Object[] { 1L, 2L }, new Object[] { "a", "b" }, Map.of("k", 1), "ACTIVE", token }
        );
        resultSet.next();

        GenericRecord record = RowMappers.auto(GenericRecord.class).map(resultSet);

        assertEquals(List.of(1L, 2L), record.ids());
        assertEquals(Set.of("a", "b"), record.tags());
        assertEquals(Map.of("k", 1), record.attrs());
        assertEquals(Status.ACTIVE, record.status());
        assertEquals(token, record.token());
    }

    @Test
    void usesCustomAdapterForParameterizedType() throws SQLException {
        JdbcTypeAdapters.register(new TypeRef<List<String>>() { }, new JdbcTypeAdapter<>() {
            @Override
            public List<String> read(ResultSet resultSet, int index, Type targetType) throws SQLException {
                String value = resultSet.getString(index);
                if (value == null || value.isBlank()) {
                    return List.of();
                }
                return List.of(value.split("\\|"));
            }
        });

        SimpleResultSet resultSet = new SimpleResultSet();
        resultSet.addColumn("TAGS", Types.VARCHAR, 0, 0);
        resultSet.addRow("alpha|beta|gamma");
        resultSet.next();

        TagRecord record = RowMappers.auto(TagRecord.class).map(resultSet);

        assertEquals(List.of("alpha", "beta", "gamma"), record.tags());
    }

    @Test
    void defaultsPrimitiveWhenNull() throws SQLException {
        SimpleResultSet resultSet = new SimpleResultSet();
        resultSet.addColumn("COUNT", Types.INTEGER, 0, 0);
        resultSet.addRow(new Object[] { null });
        resultSet.next();

        PrimitiveRecord record = RowMappers.auto(PrimitiveRecord.class).map(resultSet);

        assertEquals(0, record.count());
    }

    @Test
    void mapsBeanWithGenericFields() throws SQLException {
        SimpleResultSet resultSet = new SimpleResultSet();
        resultSet.addColumn("CODES", Types.ARRAY, 0, 0);
        resultSet.addColumn("META", Types.OTHER, 0, 0);
        resultSet.addRow(new Object[] { new Object[] { 3, 4 }, Map.of("k", "v") });
        resultSet.next();

        BeanEntity entity = RowMappers.auto(BeanEntity.class).map(resultSet);

        assertEquals(List.of(3, 4), entity.codes);
        assertEquals(Map.of("k", "v"), entity.meta);
    }

    private record GenericRecord(
        List<Long> ids,
        Set<String> tags,
        Map<String, Integer> attrs,
        Status status,
        UUID token
    ) {
    }

    private record TagRecord(List<String> tags) {
    }

    private record PrimitiveRecord(int count) {
    }

    private static final class BeanEntity {
        private List<Integer> codes;
        private Map<String, String> meta;
    }

    private enum Status {
        ACTIVE,
        INACTIVE
    }
}

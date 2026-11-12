package app.lightmove.api.core.export.service;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Rows read whole into JSON-ready maps, for an export that answers "everything held" (#919): every column the query
 * returns is in the answer, so a column a later migration adds flows in by itself, and the only ones left out are
 * those the caller names as withheld. A withheld name the query does not return fails, so a rename cannot quietly
 * put a hash or a storage key back in; so does a binary column nobody withheld, which belongs in a file of its own.
 */
@Component
@RequiredArgsConstructor
public class RowSnapshotReader {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;

    public List<Map<String, Object>> rowsOf(String sql, Map<String, ?> parameters, Set<String> withheldColumns) {
        return rowsOf(sql, parameters, withheldColumns, Set.of(), (row, binaries) -> { });
    }

    /**
     * As {@link #rowsOf(String, Map, Set)}, with {@code binaryColumns} kept out of each row and handed with it to
     * {@code files}, so a row and the file it carries are read in one query.
     */
    public List<Map<String, Object>> rowsOf(String sql, Map<String, ?> parameters, Set<String> withheldColumns,
                                            Set<String> binaryColumns, BinaryColumns files) {
        ResultSetExtractor<List<Map<String, Object>>> extractor =
                rows -> read(rows, withheldColumns, binaryColumns, files);
        List<Map<String, Object>> rows = jdbc.query(sql, parameters, extractor);
        return rows == null ? List.of() : rows;
    }

    private List<Map<String, Object>> read(ResultSet rows, Set<String> withheldColumns, Set<String> binaryColumns,
                                           BinaryColumns files) throws SQLException {
        ResultSetMetaData columns = rows.getMetaData();
        Set<String> returned = new HashSet<>();
        for (int column = 1; column <= columns.getColumnCount(); column++) {
            returned.add(columns.getColumnLabel(column));
        }
        Set<String> missing = new HashSet<>(withheldColumns);
        missing.addAll(binaryColumns);
        missing.removeAll(returned);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("withheld columns the query does not return: " + missing);
        }
        List<Map<String, Object>> answer = new ArrayList<>();
        while (rows.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            Map<String, byte[]> binaries = new LinkedHashMap<>();
            for (int column = 1; column <= columns.getColumnCount(); column++) {
                String name = columns.getColumnLabel(column);
                if (binaryColumns.contains(name)) {
                    binaries.put(name, rows.getBytes(column));
                } else if (!withheldColumns.contains(name)) {
                    row.put(name, valueOf(rows, column, name, columns.getColumnTypeName(column)));
                }
            }
            answer.add(row);
            if (!binaryColumns.isEmpty()) {
                files.accept(row, binaries);
            }
        }
        return answer;
    }

    private Object valueOf(ResultSet rows, int column, String name, String type) throws SQLException {
        Object raw = rows.getObject(column);
        if (raw == null) {
            return null;
        }
        return switch (type) {
            case "json", "jsonb" -> json.readTree(rows.getString(column));
            case "bytea" -> throw new IllegalStateException("binary column " + name + " must be withheld");
            default -> plain(raw);
        };
    }

    private static Object plain(Object raw) throws SQLException {
        return switch (raw) {
            case Timestamp timestamp -> timestamp.toInstant().toString();
            case java.sql.Date date -> date.toLocalDate().toString();
            case OffsetDateTime offset -> offset.toInstant().toString();
            case UUID id -> id.toString();
            case Array array -> elementsOf(array);
            case Number number -> number;
            case Boolean flag -> flag;
            default -> raw.toString();
        };
    }

    private static List<Object> elementsOf(Array array) throws SQLException {
        List<Object> elements = new ArrayList<>();
        for (Object element : (Object[]) array.getArray()) {
            elements.add(element == null ? null : plain(element));
        }
        return elements;
    }

    /** Takes a row's binary columns, by name, beside the row as it is answered; a null column is a null value. */
    @FunctionalInterface
    public interface BinaryColumns {
        void accept(Map<String, Object> row, Map<String, byte[]> binaries);
    }
}

package app.lightmove.api.dataimport.service;

import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.dataimport.constant.ImportTargetField;
import app.lightmove.api.dataimport.model.ParsedSheet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One row's cells, indexed by meaning rather than position. */
record RowFields(Map<ImportTargetField, String> byField,
                 Map<CustomColumnTarget, Map<String, String>> customByTarget) {

    static RowFields read(ParsedSheet sheet, List<String> row, Map<Integer, ResolvedColumn> resolved) {
        Map<ImportTargetField, String> byField = new EnumMap<>(ImportTargetField.class);
        Map<CustomColumnTarget, Map<String, String>> custom = new EnumMap<>(CustomColumnTarget.class);
        custom.put(CustomColumnTarget.COMPANY, new HashMap<>());
        custom.put(CustomColumnTarget.CANDIDATE, new HashMap<>());

        resolved.forEach((columnIndex, column) -> {
            String value = sheet.cell(row, columnIndex);
            if (value == null) {
                return;
            }
            switch (column) {
                case ResolvedColumn.BuiltInColumn(ImportTargetField field) -> byField.put(field, value);
                case ResolvedColumn.DefinedCustomColumn(var defined) ->
                        custom.get(CustomColumnTarget.fromValue(defined.target())).put(defined.fieldKey(), value);
            }
        });
        return new RowFields(byField, custom);
    }

    String field(ImportTargetField field) {
        return byField.get(field);
    }

    Map<String, String> customValues(CustomColumnTarget target) {
        return Map.copyOf(customByTarget.get(target));
    }

    /** Joined from first and last when the file splits them, as most LinkedIn and ATS exports do. */
    String personName() {
        String full = byField.get(ImportTargetField.CANDIDATE_NAME);
        if (full != null) {
            return full;
        }
        String first = byField.get(ImportTargetField.CANDIDATE_FIRST_NAME);
        String last = byField.get(ImportTargetField.CANDIDATE_LAST_NAME);
        if (first == null && last == null) {
            return null;
        }
        return (first == null ? "" : first + " ") .concat(last == null ? "" : last).trim();
    }
}

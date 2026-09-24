package app.lightmove.api.workspace.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the firm is — its business, sectors, competitors and geographies — held for the assistant to
 * tailor research to. Stored whole as the workspace's {@code persona} jsonb (V69).
 */
public record WorkspacePersona(
        String summary,
        List<String> sectors,
        List<String> competitors,
        List<String> geographies,
        String notes
) {

    public WorkspacePersona {
        summary = blankToNull(summary);
        notes = blankToNull(notes);
        sectors = distinct(sectors);
        competitors = distinct(competitors);
        geographies = distinct(geographies);
    }

    public static WorkspacePersona empty() {
        return new WorkspacePersona(null, List.of(), List.of(), List.of(), null);
    }

    /** Signup's starting point: the picked company's industry as the one sector, and nothing else. */
    public static WorkspacePersona seededFrom(WorkspaceCompany company) {
        if (company == null || company.industry() == null) {
            return empty();
        }
        return new WorkspacePersona(null, List.of(company.industry()), List.of(), List.of(), null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Trimmed, blanks dropped, and case-insensitive repeats collapsed onto the first spelling. */
    private static List<String> distinct(List<String> values) {
        if (values == null) {
            return List.of();
        }
        Map<String, String> firstSpelling = new LinkedHashMap<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                String trimmed = value.trim();
                firstSpelling.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
            }
        }
        return List.copyOf(new ArrayList<>(firstSpelling.values()));
    }
}

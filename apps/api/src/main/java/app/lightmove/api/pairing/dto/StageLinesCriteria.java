package app.lightmove.api.pairing.dto;

import java.util.List;

/**
 * A Companies grid request, untrusted as written: every token is resolved against an allowlist, and a custom column
 * sort ({@code custom:<target>:<fieldKey>}) against the mandate's own columns. Null means the stage's default.
 */
public record StageLinesCriteria(String status, String query, String executiveQuery,
                                 List<String> executiveStatuses, String search,
                                 String sort, String direction, Integer page, Integer size) {}

package app.lightmove.api.candidate.model;

import java.util.List;
import java.util.Map;

/** One kind of thing held on the person, under a stable {@code key} in the JSON, with every row as it is stored. */
public record SubjectAccessSection(String key, String title, List<Map<String, Object>> rows) {}

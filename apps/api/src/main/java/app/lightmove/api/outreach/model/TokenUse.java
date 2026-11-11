package app.lightmove.api.outreach.model;

/** One {@code {{token}}} in a text: its name, and the fallback written after its pipe (null where none). */
public record TokenUse(String name, String fallback) {}

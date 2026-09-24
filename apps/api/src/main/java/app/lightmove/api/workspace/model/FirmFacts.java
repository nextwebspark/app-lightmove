package app.lightmove.api.workspace.model;

/**
 * What the assistant is told about the firm it works for: the company the workspace was picked as,
 * its headcount where the universe carries it, and the persona an admin wrote. Any of it may be null
 * — a firm typed in by hand at signup has no company.
 */
public record FirmFacts(String name, String industry, String city, String country, String website,
                        Integer employees, WorkspacePersona persona) {}

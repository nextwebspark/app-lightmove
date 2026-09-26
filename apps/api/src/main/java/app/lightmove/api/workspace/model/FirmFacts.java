package app.lightmove.api.workspace.model;

/** What the assistant is told about its firm. Any part may be null. */
public record FirmFacts(String name, String industry, String city, String country, String website,
                        Integer employees, WorkspacePersona persona) {}

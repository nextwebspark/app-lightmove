package app.lightmove.api.common.persona.model;

/** The company a mandate hires for, as the assistant is told about it. Any part may be null. */
public record HiringCompanyProfile(String name, String industry, String city, String country, String website,
                                   Integer employees, HiringPersona persona) {
}

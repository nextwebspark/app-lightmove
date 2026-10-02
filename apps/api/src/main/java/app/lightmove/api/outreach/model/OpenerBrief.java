package app.lightmove.api.outreach.model;

/**
 * What the opener is told about the role. Deliberately no hiring company: a search is confidential, so
 * the business unit or client is never named to the model and can never be named back.
 */
public record OpenerBrief(String roleTitle, String seniority, String sector, String locationCity,
                          String locationCountry) {}

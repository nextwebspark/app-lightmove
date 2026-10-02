package app.lightmove.api.outreach.model;

/** A person's first email as the consultant approved it, opener included. */
public record ReviewedFirstEmail(String subject, String body, String opener, boolean openerEdited) {}

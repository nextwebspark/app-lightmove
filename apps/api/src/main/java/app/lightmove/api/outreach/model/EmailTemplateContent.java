package app.lightmove.api.outreach.model;

/** What an email template says: its name, its subject (null where it keeps none) and its Markdown body. */
public record EmailTemplateContent(String name, String subject, String body) {}

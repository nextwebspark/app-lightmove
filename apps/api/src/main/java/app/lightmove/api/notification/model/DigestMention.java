package app.lightmove.api.notification.model;

/** An unread mention the morning digest names: who wrote it, about whom, on which position, and where it opens. */
public record DigestMention(String actorName, String executiveName, String positionTitle, String link) {}

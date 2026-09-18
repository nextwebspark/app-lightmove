package app.lightmove.api.core.email.render;

/**
 * One piece of an email's body. The set is closed on purpose: a template states what it wants to say,
 * and {@link EmailRenderer} owns every decision about how that looks in HTML and in plain text.
 */
public sealed interface EmailBlock permits EmailParagraph, EmailAction, EmailNote {}

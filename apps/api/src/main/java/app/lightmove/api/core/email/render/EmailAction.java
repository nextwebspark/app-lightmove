package app.lightmove.api.core.email.render;

/** The one thing the email asks for: a labelled button in HTML, the bare link in plain text. */
public record EmailAction(String label, String href) implements EmailBlock {}

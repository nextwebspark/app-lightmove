package app.lightmove.api.outreach.model;

/** A signature as an email carries it: HTML for the message, and its words for the plain text and the preview. */
public record RenderedSignature(String html, String text) {}

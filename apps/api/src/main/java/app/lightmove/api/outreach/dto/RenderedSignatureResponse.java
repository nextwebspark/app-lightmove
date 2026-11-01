package app.lightmove.api.outreach.dto;

/** A signature drawn without being saved: the HTML an email would carry, and its words. */
public record RenderedSignatureResponse(String html, String text) {}

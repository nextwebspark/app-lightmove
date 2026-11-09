package app.lightmove.api.outreach.model;

/** A signature as an email carries it: HTML, since every email Uncava sends is a single HTML part. */
public record RenderedSignature(String html) {}

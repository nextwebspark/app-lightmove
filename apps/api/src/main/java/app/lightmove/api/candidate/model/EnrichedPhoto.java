package app.lightmove.api.candidate.model;

import org.springframework.modulith.NamedInterface;

/**
 * A profile photo the enrichment already downloaded — bytes, not a URL, because provider image links
 * carry expiring signatures and a stored URL is a broken avatar a few weeks later.
 */
@NamedInterface(name = "api", propagate = false)
public record EnrichedPhoto(byte[] content, String contentType) {}

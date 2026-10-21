package app.lightmove.api.outreach.dto;

/** What one email may carry, as the server will hold it to: the composer checks the same before sending. */
public record AttachmentLimitsResponse(int maxFiles, long maxFileBytes, long maxTotalBytes) {}

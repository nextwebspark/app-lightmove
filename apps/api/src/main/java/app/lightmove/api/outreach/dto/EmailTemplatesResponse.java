package app.lightmove.api.outreach.dto;

import java.util.List;

/**
 * The caller's own email templates and the workspace's shared ones, by name. {@code limit} is how many one person may
 * keep, and {@code bookingLinkOffered} whether {@code {{bookingLink}}} may be used.
 */
public record EmailTemplatesResponse(List<EmailTemplateResponse> templates, int limit, boolean bookingLinkOffered) {}

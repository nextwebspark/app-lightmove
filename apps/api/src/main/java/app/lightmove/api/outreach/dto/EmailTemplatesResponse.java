package app.lightmove.api.outreach.dto;

import java.util.List;
import java.util.UUID;

/**
 * The caller's own email templates and the workspace's shared ones, by name. {@code limit} is how many one person may
 * keep, {@code bookingLinkOffered} whether {@code {{bookingLink}}} may be used, and {@code recentlyUsed} the ids of the
 * five the caller's own emails came from most recently, newest first.
 */
public record EmailTemplatesResponse(List<EmailTemplateResponse> templates, int limit, boolean bookingLinkOffered,
                                     List<UUID> recentlyUsed) {}

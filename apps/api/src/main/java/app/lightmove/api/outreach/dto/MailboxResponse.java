package app.lightmove.api.outreach.dto;

import java.util.List;

/**
 * The caller's own mailbox, and whether one can be connected at all.
 *
 * @param providers  the mailbox hosts on offer, in the mail service's names
 * @param connection null until the caller connects one
 * @param bookingLinkOffered whether sequences may use {@code {{bookingLink}}} here
 */
public record MailboxResponse(boolean offered, List<String> providers, ConnectedMailboxResponse connection,
                              boolean bookingLinkOffered) {}

package app.lightmove.api.outreach.service;

import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorException;

/**
 * A calendar's answer that it no longer holds an event: a 404, or Google's 410 for one deleted. Never the mailbox's
 * access withdrawn, which {@code MailboxService.isAccessWithdrawn} reads a grant-level 404 as.
 */
final class CalendarEventGone {

    private CalendarEventGone() {}

    static boolean isGone(VendorException failed) {
        return failed.getKind() == VendorFailureKind.NOT_FOUND || failed.getKind() == VendorFailureKind.GONE;
    }
}

package app.lightmove.api.core.email.render;

import app.lightmove.api.core.email.constant.EmailTone;

/**
 * One line of a card: a linked name, what it is about, and optionally a leading time, a reason under it and a badge
 * on the right. Start from {@link #of} and add the optional parts with the {@code with…} methods.
 */
public record EmailRow(String leading, String primary, String secondary, String detail, String badge,
                       EmailTone badgeTone, String href) {

    /** {@code secondary} may be null. */
    public static EmailRow of(String primary, String secondary, String href) {
        return new EmailRow(null, primary, secondary, null, null, null, href);
    }

    public EmailRow withLeading(String time) {
        return new EmailRow(time, primary, secondary, detail, badge, badgeTone, href);
    }

    public EmailRow withDetail(String reason) {
        return new EmailRow(leading, primary, secondary, reason, badge, badgeTone, href);
    }

    public EmailRow withBadge(String label, EmailTone tone) {
        return new EmailRow(leading, primary, secondary, detail, label, tone, href);
    }
}

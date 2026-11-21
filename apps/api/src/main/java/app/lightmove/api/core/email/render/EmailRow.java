package app.lightmove.api.core.email.render;

import app.lightmove.api.core.email.constant.EmailTone;

/**
 * One line of a card: a linked name, what it is about, an optional reason, an optional leading time and an
 * optional badge on the right. Every field but {@code primary} and {@code href} may be null.
 */
public record EmailRow(String leading, String primary, String secondary, String detail, String badge,
                       EmailTone badgeTone, String href) {}

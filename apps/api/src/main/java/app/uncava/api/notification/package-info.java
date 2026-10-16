/**
 * <b>Notifications — a person's bell</b> (epic #862, V133). {@code NotificationRules} turns events other features
 * publish (a task assigned, falling due or handed over, a mention, an outreach reply, bounce or booking, a seat on a
 * team) into rows, re-checking every recipient first; {@code NotificationWriter} folds rows of one group while
 * unread and nudges the person's open tabs over {@code core/stream}'s per-user channel; {@code NotificationEmails}
 * emails what stays unread and sends the morning digest. A row names a note and never quotes it.
 *
 * <p>Depends on {@code task}, {@code candidate}, {@code project} and {@code outreach} (the mailbox's time zone);
 * nothing depends back.
 */
package app.uncava.api.notification;

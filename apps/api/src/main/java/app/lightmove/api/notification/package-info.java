/**
 * <b>Notifications — a person's bell</b> (epic #862, V133): {@code NotificationRules} turns events into rows,
 * re-checking each recipient; {@code NotificationWriter} folds and nudges open tabs; {@code NotificationEmails} sends
 * unread emails and the digest. A row names a note, never quotes it. Nothing depends back on this package.
 */
@ApplicationModule(allowedDependencies = {
        "account :: api",
        "candidate :: api",
        "project :: api",
        "task :: api",
        "workspace :: api"
})
package app.lightmove.api.notification;

import org.springframework.modulith.ApplicationModule;

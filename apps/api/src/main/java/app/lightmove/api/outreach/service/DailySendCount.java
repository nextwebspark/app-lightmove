package app.lightmove.api.outreach.service;

import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.repository.OutreachMessageRepository;
import java.time.Instant;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** How many emails a mailbox has sent today, by its own clock: what a consultant's own email is held to. */
@Component
@RequiredArgsConstructor
public class DailySendCount {

    private final OutreachMessageRepository messages;

    public long of(MailboxConnection mailbox, Instant now) {
        Instant midnight = LocalDate.ofInstant(now, mailbox.zone()).atStartOfDay(mailbox.zone()).toInstant();
        return messages.countByWorkspaceIdAndSenderUserIdAndSentAtGreaterThanEqual(mailbox.getWorkspaceId(),
                mailbox.getUserId(), midnight);
    }
}

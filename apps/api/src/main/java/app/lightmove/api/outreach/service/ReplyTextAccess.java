package app.lightmove.api.outreach.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.outreach.constant.MailboxGatewayKind;
import app.lightmove.api.outreach.model.MailboxConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Whether the words of a reply into a mailbox's threads may be read: our own gateway's consent says, as the person
 * granted it; a Nylas grant reports no reach, so its app's is a setting.
 */
@Component
@RequiredArgsConstructor
public class ReplyTextAccess {

    private final LightMoveProperties properties;

    public boolean readsText(MailboxConnection mailbox) {
        return mailbox.getGateway() == MailboxGatewayKind.NYLAS
                ? properties.outreach().nylas().readsReplies()
                : mailbox.isReplyTextGranted();
    }

    /** A mailbox of ours connected before Uncava asked for the read, or whose consent left it unticked. */
    public boolean wantsReconnectToRead(MailboxConnection mailbox) {
        return mailbox.getGateway() == MailboxGatewayKind.DIRECT && !mailbox.isReplyTextGranted();
    }
}

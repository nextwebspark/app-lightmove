package app.uncava.api.outreach.service;

import app.uncava.api.candidate.model.OutreachRecipient;
import app.uncava.api.core.security.model.User;
import app.uncava.api.core.security.repository.UserRepository;
import app.uncava.api.outreach.model.SenderContext;
import app.uncava.api.outreach.model.SequenceTokens;
import app.uncava.api.outreach.repository.MailboxConnectionRepository;
import app.uncava.api.position.service.PositionService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What a sequence's tokens are filled with — the same at Start, for the first email, and at send, for the follow-ups. */
@Component
@RequiredArgsConstructor
class OutreachPersonalisation {

    private final PositionService positions;
    private final UserRepository users;
    private final MailboxConnectionRepository mailboxes;
    private final BookingPages bookingPages;

    SenderContext senderContextOf(UUID userId, UUID workspaceId, UUID projectId) {
        String positionTitle = positions.briefOf(workspaceId, projectId).details().roleTitle();
        String senderFirstName = users.findById(userId)
                .map(User::getFullName)
                .map(SequenceTokens::firstNameOf)
                .orElse(null);
        String bookingLink = mailboxes.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(bookingPages::linkOf)
                .orElse(null);
        return new SenderContext(positionTitle, senderFirstName, bookingLink);
    }

    static SequenceTokens tokensOf(OutreachRecipient recipient, SenderContext sender, String opener) {
        String location = recipient.locationCity() != null ? recipient.locationCity() : recipient.locationCountry();
        return new SequenceTokens(SequenceTokens.firstNameOf(recipient.fullName()), recipient.title(),
                recipient.companyName(), sender.positionTitle(), location, sender.senderFirstName(), opener,
                sender.bookingLink());
    }
}

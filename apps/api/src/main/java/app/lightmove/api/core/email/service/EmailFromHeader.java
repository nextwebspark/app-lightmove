package app.lightmove.api.core.email.service;

import app.lightmove.api.core.config.EmailSettings;
import app.lightmove.api.core.email.constant.EmailSenderIdentity;
import app.lightmove.api.core.email.model.EmailMessage;
import java.util.regex.Pattern;

/** The From header an email leaves with: Uncava's address for its identity, a firm named first as "{firm} via Uncava". */
final class EmailFromHeader {

    /** What would end a quoted display name early, start a quoted-pair, or start a header of its own. */
    private static final Pattern UNSAFE_IN_DISPLAY_NAME = Pattern.compile("[\"\\\\<>\\r\\n]");

    private final String fromName;
    private final String accountAddress;
    private final String privacyAddress;

    EmailFromHeader(EmailSettings settings) {
        this.fromName = settings.fromName();
        this.accountAddress = settings.fromAddress();
        this.privacyAddress = settings.privacyFromAddress();
    }

    String of(EmailMessage message) {
        String address = addressOf(message.senderIdentity());
        if (message.senderName() == null || message.senderName().isBlank()) {
            return "%s <%s>".formatted(fromName, address);
        }
        String shown = UNSAFE_IN_DISPLAY_NAME.matcher(message.senderName()).replaceAll("").strip();
        return "\"%s via %s\" <%s>".formatted(shown, fromName, address);
    }

    private String addressOf(EmailSenderIdentity identity) {
        return switch (identity) {
            case ACCOUNT -> accountAddress;
            case PRIVACY -> privacyAddress;
        };
    }
}

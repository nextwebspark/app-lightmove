package app.lightmove.api.outreach.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.OutgoingEmail;
import app.lightmove.api.outreach.model.SentEmail;
import java.net.URI;
import java.util.List;

/** A deployment without a mail service: outreach is not offered, and anything that reaches here is refused. */
public class UnconfiguredMailboxGateway implements MailboxGateway {

    @Override
    public boolean isOffered() {
        return false;
    }

    @Override
    public List<String> providers() {
        return List.of();
    }

    @Override
    public URI authorizationUri(String provider, String loginHint, String state, URI redirectUri) {
        throw unavailable();
    }

    @Override
    public GrantedMailbox redeem(String code, URI redirectUri) {
        throw unavailable();
    }

    @Override
    public SentEmail send(String grantId, OutgoingEmail email) {
        throw unavailable();
    }

    @Override
    public void revoke(String grantId) {
        throw unavailable();
    }

    private static ApiException unavailable() {
        return ApiException.of(ErrorCode.MAILBOX_UNAVAILABLE);
    }
}

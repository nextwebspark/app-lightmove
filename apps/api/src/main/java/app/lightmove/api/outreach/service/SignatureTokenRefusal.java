package app.lightmove.api.outreach.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.outreach.model.SequenceTokens;

/**
 * A signature is never part of an email's words: Include signature adds the sender's own when the email goes, or when
 * a sequence starts. {@code {{signature}}} is a retired token (#1277), refused by name in a template or a sequence step
 * so a habit of placing it is answered rather than sent as typed; it is no {@link SequenceTokens} token.
 */
final class SignatureTokenRefusal {

    private static final String SIGNATURE = "signature";

    private SignatureTokenRefusal() {
    }

    static void refuseIn(String field, String text) {
        if (SequenceTokens.uses(text, SIGNATURE)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, field,
                    "Leave {{signature}} out: Include signature adds yours when you send");
        }
    }
}

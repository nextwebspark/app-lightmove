package app.lightmove.api.outreach.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.outreach.model.SequenceTokens;

/**
 * A signature is never part of an email's words: Include signature adds the sender's own when the email goes, or when
 * a sequence starts. A template or a sequence step holding {@code {{signature}}} is refused, since an unknown token in
 * a step would otherwise reach an executive as typed.
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

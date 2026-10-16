package app.uncava.api.enrichment.candidate.service;

import app.uncava.api.core.error.constant.ErrorCode;
import app.uncava.api.core.error.model.ApiException;

/**
 * The refusal for a run the prompt guard stopped. It is the words on file, not the model, so pressing again fails the
 * same way — never "try again in a moment".
 */
final class AiRefusals {

    private AiRefusals() {}

    static ApiException blockedText() {
        return ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                "Something on file for this executive — the profile, a note or a document — reads like an "
                        + "instruction to the AI. Reword it and try again.");
    }
}

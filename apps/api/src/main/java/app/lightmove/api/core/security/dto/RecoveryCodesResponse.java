package app.lightmove.api.core.security.dto;

import java.util.List;

/** A new set of recovery codes, once: only their hashes are kept. */
public record RecoveryCodesResponse(List<String> recoveryCodes) {

    @Override
    public String toString() {
        return "RecoveryCodesResponse[recoveryCodes=<redacted>]";
    }
}

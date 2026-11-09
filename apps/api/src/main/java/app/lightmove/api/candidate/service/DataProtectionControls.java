package app.lightmove.api.candidate.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Whether this deployment offers the people controls of epic #917 ({@code lightmove.data-protection.enabled}): the
 * suppression list, erasure, restriction of processing and the candidate retention review. Off, each route is a 404.
 */
@Component
public class DataProtectionControls {

    private final boolean enabled;

    public DataProtectionControls(@Value("${lightmove.data-protection.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isOffered() {
        return enabled;
    }

    public void requireOffered() {
        if (!enabled) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
    }
}

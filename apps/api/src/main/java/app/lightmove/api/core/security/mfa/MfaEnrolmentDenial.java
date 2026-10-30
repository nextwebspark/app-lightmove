package app.lightmove.api.core.security.mfa;

import org.springframework.security.authorization.AuthorizationDecision;

/**
 * A request refused because the session's workspace requires a factor the caller has not set up. Its own type so
 * the filter chain's access-denied handler can answer {@code MFA_ENROLMENT_REQUIRED} rather than a bare 403.
 */
public final class MfaEnrolmentDenial extends AuthorizationDecision {

    public MfaEnrolmentDenial() {
        super(false);
    }
}

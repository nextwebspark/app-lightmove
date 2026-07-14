package app.lightmove.api.auth.domain;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * A user has proved they read the mailbox they signed up with.
 *
 * <p>Published rather than called, because what happens next belongs to another feature: the workspace
 * package materialises whatever the user filled into the signup wizard while unverified. Auth should
 * not have to know that onboarding exists — and, if a third feature ever needs to react to verification,
 * it should not have to edit {@code VerificationService} to do so.
 *
 * <p>Handled <b>synchronously, inside the verifying transaction</b>. Not an implementation detail: the
 * workspace must come into existence with the verification, or neither must. A user who clicks their
 * link and lands in an account with a verified email and no organisation — because an asynchronous
 * listener failed quietly — has nowhere to go and no way to retry.
 *
 * @param request carried so the audit trail records the IP and user agent of the click that caused the
 *                workspace to exist. Safe because the listener runs on this very thread.
 */
public record EmailVerifiedEvent(UUID userId, HttpServletRequest request) {
}

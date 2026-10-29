package app.lightmove.api.core.security.service;

import app.lightmove.api.core.security.mfa.MfaChallengeService;
import app.lightmove.api.core.security.model.SignInOutcome;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.token.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place a proved password or mailbox becomes a session — or, for a person with a second factor, a challenge
 * for the code. Login, a password reset and a verification link all ask here: a reset that signed straight in would let
 * whoever reads the inbox skip the factor, which is the one thing it exists to stop.
 */
@Service
@RequiredArgsConstructor
public class SignInCompletion {

    private final MfaChallengeService challenges;
    private final TokenService tokens;
    private final WorkspaceSelection selection;

    @Transactional
    public SignInOutcome complete(User user, HttpServletRequest request) {
        if (challenges.owesCode(user.getId())) {
            return challenges.issue(user, Instant.now());
        }
        return tokens.issue(user, selection.signIn(user), request);
    }
}

package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.auth.application.AuthService;
import app.lightmove.api.auth.application.AuthenticatedSession;
import app.lightmove.api.auth.application.SignupCommand;
import app.lightmove.api.auth.domain.RevokeReason;
import app.lightmove.api.auth.infrastructure.RefreshTokenRepository;
import app.lightmove.api.common.error.ApiException;
import app.lightmove.api.common.error.ErrorCode;
import app.lightmove.api.common.security.Tokens;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Two clients redeem the same refresh token at the same moment. Exactly one may win.
 *
 * <p>This is not a hypothetical. It is the shape of the attack that rotation exists to detect: a thief
 * holding a stolen token races the real user, and both present it within milliseconds of each other.
 *
 * <p>Rotation is read-then-write — check the token is not revoked, then burn it and mint a successor.
 * Without a row lock the two transactions interleave: both read it un-revoked, both conclude it is
 * fresh, both mint successors in the same family, and <b>reuse detection never fires</b>. The system
 * reports nothing wrong while an attacker walks away with a working session.
 *
 * <p>The failure is invisible to every single-threaded test, which is why it survived a suite that
 * already tested reuse detection — sequentially, where it passes.
 */
@IntegrationTest
class RefreshRotationConcurrencyTest {

    private static final AtomicInteger RUN = new AtomicInteger();

    @Autowired AuthService auth;
    @Autowired RefreshTokenRepository refreshTokens;

    @Test
    @DisplayName("two simultaneous refreshes of one token: one wins, the other is caught as reuse")
    void concurrentRefreshTripsReuseDetection() throws Exception {
        String domain = "race%d.example".formatted(RUN.incrementAndGet());
        AuthenticatedSession session = auth.signup(
                new SignupCommand("Alok Kumar", "alok@" + domain, "secret123", true), null);

        String stolen = session.tokens().refreshToken();

        // Both threads start from the same token. Whichever the database serialises second must find it
        // already revoked — that is what the row lock guarantees.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Outcome> redeem = () -> {
                try {
                    auth.refresh(stolen, null);
                    return Outcome.ACCEPTED;
                } catch (ApiException e) {
                    return e.getCode() == ErrorCode.REFRESH_TOKEN_REUSED
                            ? Outcome.CAUGHT_AS_REUSE
                            : Outcome.REJECTED_OTHERWISE;
                }
            };

            List<Future<Outcome>> results = pool.invokeAll(List.of(redeem, redeem));
            List<Outcome> outcomes = List.of(results.get(0).get(), results.get(1).get());

            assertThat(outcomes)
                    .as("exactly one refresh may succeed; the loser must be caught as reuse, not "
                            + "quietly issued a second live token")
                    .containsExactlyInAnyOrder(Outcome.ACCEPTED, Outcome.CAUGHT_AS_REUSE);
        } finally {
            pool.shutdownNow();
        }

        // And the detection must have teeth: catching the reuse revokes the whole family, so the
        // successor the winner was just issued is dead too. Both parties sign in again — a lost session
        // is an acceptable price, leaving a thief with a live credential is not.
        var presented = refreshTokens.findByTokenHash(Tokens.hash(stolen)).orElseThrow();

        assertThat(refreshTokens.findAll().stream()
                .filter(t -> t.getFamilyId().equals(presented.getFamilyId())))
                .as("every token in the family is dead once reuse is detected — including the successor "
                        + "the race's winner was issued a moment ago")
                .allMatch(t -> t.getRevokedAt() != null);

        // The presented token reads ROTATED, not REUSE_DETECTED, and that is right: the winner burned it
        // legitimately, and revokeFamily only writes rows that are still live. The reuse is recorded on
        // the *successor* it was rotated into — and in the audit log, which is where an investigator
        // looks. Asserting REUSE_DETECTED here would be asserting a bug.
        assertThat(presented.getRevokedReason()).isEqualTo(RevokeReason.ROTATED);

        assertThat(refreshTokens.findAll().stream()
                .filter(t -> t.getFamilyId().equals(presented.getFamilyId()))
                .anyMatch(t -> t.getRevokedReason() == RevokeReason.REUSE_DETECTED))
                .as("the family carries the reuse verdict")
                .isTrue();
    }

    private enum Outcome {
        ACCEPTED,
        CAUGHT_AS_REUSE,
        REJECTED_OTHERWISE,
    }
}

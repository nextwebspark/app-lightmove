package app.lightmove.api.core.vendor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.vendor.constant.VendorFailureKind;
import app.lightmove.api.core.vendor.model.VendorAttemptResult;
import app.lightmove.api.core.vendor.model.VendorCall;
import app.lightmove.api.core.vendor.model.VendorException;
import app.lightmove.api.core.vendor.model.VendorFailure;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The cascade's rule, with no vendor and no HTTP: which failures move on, and which stop everything.
 *
 * <p>Every test here counts the attempts actually made, because that count is the money. A chain that
 * falls through a bad key or an empty credit balance pays for the same refusal once per step.
 */
class VendorAttemptChainTest {

    private static final VendorCall CALL = VendorCall.read("acme", "search");

    @Test
    @DisplayName("the first attempt that answers wins, and nothing after it is asked")
    void stopsAtTheFirstAnswer() {
        List<String> made = new ArrayList<>();

        VendorAttemptResult<String> result = VendorAttemptChain.<String>forLookup("people at a company")
                .attempt("linkedin-url", () -> record(made, "linkedin-url", Optional.of("found")))
                .attempt("website", () -> record(made, "website", Optional.of("also found")))
                .run();

        assertThat(result.value()).contains("found");
        assertThat(result.answeredBy()).isEqualTo("linkedin-url");
        assertThat(made).containsExactly("linkedin-url");
    }

    @Test
    @DisplayName("an empty answer moves on to the next way of asking")
    void emptyAdvances() {
        List<String> made = new ArrayList<>();

        VendorAttemptResult<String> result = VendorAttemptChain.<String>forLookup("people at a company")
                .attempt("linkedin-url", () -> record(made, "linkedin-url", Optional.empty()))
                .attempt("website", () -> record(made, "website", Optional.of("found")))
                .run();

        assertThat(result.answeredBy()).isEqualTo("website");
        assertThat(made).containsExactly("linkedin-url", "website");
    }

    @Test
    @DisplayName("a 404 is an answer of 'nobody', so it advances exactly like an empty result")
    void notFoundAdvances() {
        List<String> made = new ArrayList<>();

        VendorAttemptResult<String> result = VendorAttemptChain.<String>forLookup("people at a company")
                .attempt("linkedin-url", () -> {
                    made.add("linkedin-url");
                    throw failure(VendorFailureKind.NOT_FOUND);
                })
                .attempt("website", () -> record(made, "website", Optional.of("found")))
                .run();

        assertThat(result.answeredBy()).isEqualTo("website");
        assertThat(made).containsExactly("linkedin-url", "website");
    }

    @Test
    @DisplayName("an empty credit balance stops the chain dead rather than spending it twice over")
    void quotaExhaustedAbortsWithoutTouchingLaterAttempts() {
        List<String> made = new ArrayList<>();

        assertThatThrownBy(() -> VendorAttemptChain.<String>forLookup("people at a company")
                .attempt("linkedin-url", () -> {
                    made.add("linkedin-url");
                    throw failure(VendorFailureKind.QUOTA_EXHAUSTED);
                })
                .attempt("website", () -> record(made, "website", Optional.of("found")))
                .run())
                .isInstanceOf(VendorException.class);

        // The whole point: the second lookup was never paid for.
        assertThat(made).containsExactly("linkedin-url");
    }

    @Test
    @DisplayName("being throttled stops the chain too — the next endpoint is throttled by the same key")
    void rateLimitedAbortsRatherThanAdvancing() {
        List<String> made = new ArrayList<>();

        assertThatThrownBy(() -> VendorAttemptChain.<String>forLookup("people at a company")
                .attempt("linkedin-url", () -> {
                    made.add("linkedin-url");
                    throw failure(VendorFailureKind.RATE_LIMITED);
                })
                .attempt("website", () -> record(made, "website", Optional.of("found")))
                .run())
                .isInstanceOf(VendorException.class);

        assertThat(made).containsExactly("linkedin-url");
    }

    @Test
    @DisplayName("a bad key stops the chain — every remaining endpoint uses the same key")
    void credentialsAbortTheChain() {
        List<String> made = new ArrayList<>();

        assertThatThrownBy(() -> VendorAttemptChain.<String>forLookup("people at a company")
                .attempt("linkedin-url", () -> {
                    made.add("linkedin-url");
                    throw failure(VendorFailureKind.CREDENTIALS);
                })
                .attempt("website", () -> record(made, "website", Optional.of("found")))
                .run())
                .isInstanceOf(VendorException.class);

        assertThat(made).containsExactly("linkedin-url");
    }

    @Test
    @DisplayName("when nobody answers, the result says so and names every way we tried")
    void exhaustedChainReportsWhatWasTried() {
        VendorAttemptResult<String> result = VendorAttemptChain.<String>forLookup("people at a company")
                .attempt("linkedin-url", Optional::empty)
                .attempt("website", Optional::empty)
                .run();

        assertThat(result.isAnswered()).isFalse();
        assertThat(result.answeredBy()).isNull();
        assertThat(result.attemptsMade()).containsExactly("linkedin-url", "website");
    }

    private static Optional<String> record(List<String> made, String name, Optional<String> answer) {
        made.add(name);
        return answer;
    }

    private static VendorException failure(VendorFailureKind kind) {
        return new VendorException(CALL, VendorFailure.of(kind), "from a test");
    }
}

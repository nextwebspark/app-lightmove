package app.lightmove.api.core.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The wire token is a contract with the browser, so the only things worth pinning are that it survives
 * a round trip and that it does not depend on where the server happens to be running.
 */
class ProjectStreamKindTest {

    private final Locale original = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(original);
    }

    @Test
    @DisplayName("every kind survives the trip out to the wire and back")
    void everyKindRoundTrips() {
        for (ProjectStreamKind kind : ProjectStreamKind.values()) {
            assertThat(ProjectStreamKind.fromWire(kind.wire())).contains(kind);
        }
    }

    @Test
    @DisplayName("the wire token does not depend on the server's locale")
    void theWireTokenIsLocaleIndependent() {
        // Turkish lowercases I to a dotless i, so a default-locale toLowerCase() would publish
        // "candidate-enriched" as "candıdate-enrıched" and the SPA would stop recognising its own
        // events on a JVM that happened to boot there.
        Locale.setDefault(Locale.forLanguageTag("tr"));

        assertThat(ProjectStreamKind.CANDIDATE_ENRICHED.wire()).isEqualTo("candidate-enriched");
    }

    @Test
    @DisplayName("a token this version does not know is refused rather than guessed at")
    void anUnknownTokenIsEmpty() {
        assertThat(ProjectStreamKind.fromWire("candidate-shortlisted")).isEmpty();
    }
}

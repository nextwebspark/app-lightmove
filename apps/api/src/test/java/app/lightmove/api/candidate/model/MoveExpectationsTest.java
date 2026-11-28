package app.lightmove.api.candidate.model;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.ContactSource;
import app.lightmove.api.candidate.constant.Mobility;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What an executive expects of a move: a stated nought is a fact, a blank is not, and nobody's answer is lost. */
class MoveExpectationsTest {

    private static final MoveExpectations TOLD_US = new MoveExpectations(1_700_000L, 4_000_000L,
            "Wants the LTIP to carry over", Mobility.REGION, "GCC; family stays in Abu Dhabi", "Jan 2027");

    @Test
    @DisplayName("blank words are not established, so a cleared field reads as cleared")
    void blankWordsAreNotEstablished() {
        MoveExpectations cleared = new MoveExpectations(null, null, "  ", null, "", " ");

        assertThat(cleared.isUnknown()).isTrue();
    }

    @Test
    @DisplayName("an expected figure of nought is a stated expectation, not an unknown one")
    void aStatedNoughtIsKnown() {
        assertThat(new MoveExpectations(0L, null, null, null, null, null).isUnknown()).isFalse();
    }

    @Test
    @DisplayName("a save replaces what they expect, a cleared field included")
    void aSaveReplacesTheExpectations() {
        Person person = Person.founded(UUID.randomUUID(), UUID.randomUUID(), CandidateSource.MANUAL,
                expecting(TOLD_US));

        person.describe(expecting(new MoveExpectations(null, 4_200_000L, null, Mobility.INTERNATIONAL, null,
                "Immediately")), ContactSource.MANUAL);

        assertThat(person.expectations()).isEqualTo(new MoveExpectations(null, 4_200_000L, null,
                Mobility.INTERNATIONAL, null, "Immediately"));
    }

    @Test
    @DisplayName("another position filing someone known fills what they expect only where nobody recorded it")
    void anotherFilingNeverOverwritesWhatTheyExpect() {
        Person recorded = Person.founded(UUID.randomUUID(), UUID.randomUUID(), CandidateSource.MANUAL,
                expecting(TOLD_US));
        Person unasked = Person.founded(UUID.randomUUID(), UUID.randomUUID(), CandidateSource.MANUAL,
                expecting(MoveExpectations.unknown()));
        MoveExpectations fromTheSheet = new MoveExpectations(null, 3_000_000L, null, Mobility.NONE, null, null);

        recorded.fillFrom(expecting(fromTheSheet), ContactSource.CSV);
        unasked.fillFrom(expecting(fromTheSheet), ContactSource.CSV);

        assertThat(recorded.expectations()).isEqualTo(TOLD_US);
        assertThat(unasked.expectations()).isEqualTo(fromTheSheet);
    }

    @Test
    @DisplayName("figures in another currency would relabel an expected package, never words alone")
    void onlyHeldFiguresCanBeRelabelled() {
        MoveExpectations wordsOnly = new MoveExpectations(null, null, "Wants a group remit", Mobility.REGION, null,
                null);

        assertThat(TOLD_US.wouldRelabel("AED", "USD")).isTrue();
        assertThat(TOLD_US.wouldRelabel("AED", "aed")).isFalse();
        assertThat(TOLD_US.wouldRelabel(null, "USD")).isFalse();
        assertThat(wordsOnly.wouldRelabel("AED", "USD")).isFalse();
    }

    private static CandidateDetails expecting(MoveExpectations expectations) {
        return new CandidateDetails("Sample Person", null, null, null, null, null, null, null, null, null, null,
                null, null, null, CandidateCompensation.unknown(), expectations, CandidateProfile.empty(), null);
    }
}

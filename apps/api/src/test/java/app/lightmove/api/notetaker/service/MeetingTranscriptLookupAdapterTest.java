package app.lightmove.api.notetaker.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.constant.CallSpeakerRole;
import app.lightmove.api.notetaker.model.TranscriptLine;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MeetingTranscriptLookupAdapterTest {

    private static final Set<String> TEAM = Set.of("yara haddad");

    @Test
    @DisplayName("the team's voice is the interviewer's, the executive's the candidate's, and anyone else's neither")
    void everyVoiceIsGivenItsRole() {
        Set<String> executive = Set.of("Priya Raman");

        assertThat(roleOf("Yara Haddad", executive)).isEqualTo(CallSpeakerRole.TEAM);
        assertThat(roleOf("Priya Raman", executive)).isEqualTo(CallSpeakerRole.CANDIDATE);
        assertThat(roleOf("Omar Saleh", executive)).isEqualTo(CallSpeakerRole.OTHER);
        assertThat(roleOf(null, executive)).isEqualTo(CallSpeakerRole.OTHER);
    }

    @Test
    @DisplayName("on a call where no voice is told to be the executive's, nobody outside the team is confirmed as them")
    void noExecutiveVoiceLeavesEveryOtherVoiceUnconfirmed() {
        assertThat(roleOf("Yara Haddad", Set.of())).isEqualTo(CallSpeakerRole.TEAM);
        assertThat(roleOf("iPhone", Set.of())).isEqualTo(CallSpeakerRole.UNCONFIRMED);
        assertThat(roleOf("Omar Saleh", Set.of())).isEqualTo(CallSpeakerRole.UNCONFIRMED);
    }

    private static CallSpeakerRole roleOf(String speaker, Set<String> executiveVoices) {
        return MeetingTranscriptLookupAdapter.roleOf(new TranscriptLine(Instant.EPOCH, speaker, "words", true), TEAM,
                executiveVoices);
    }
}

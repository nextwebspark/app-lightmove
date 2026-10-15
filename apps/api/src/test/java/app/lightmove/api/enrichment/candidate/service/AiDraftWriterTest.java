package app.lightmove.api.enrichment.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.candidate.dto.CandidateScorecardResponse;
import app.lightmove.api.candidate.model.AiScorecardDraft;
import app.lightmove.api.candidate.model.CandidateScorecard;
import app.lightmove.api.candidate.model.ScorecardReport;
import app.lightmove.api.candidate.service.CandidateScorecardService;
import app.lightmove.api.candidate.service.CandidateService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class AiDraftWriterTest {

    private static final UUID WORKSPACE = UUID.randomUUID();
    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID CANDIDATE = UUID.randomUUID();
    private static final UUID CONSULTANT = UUID.randomUUID();
    private static final AiScorecardDraft DRAFT = new AiScorecardDraft(List.of(), ScorecardReport.empty());

    private final CandidateScorecardService scorecards = mock(CandidateScorecardService.class);
    private final AiDraftWriter writer = new AiDraftWriter(mock(CandidateService.class), scorecards);

    @Test
    @DisplayName("a paid draft that loses a race to an edit is written once more over the fresh row")
    void aRacedWriteIsAskedOnceMore() {
        CandidateScorecardResponse written = mock(CandidateScorecardResponse.class);
        when(scorecards.applyAiDraft(any(), any(), any(), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(CandidateScorecard.class, CANDIDATE))
                .thenReturn(written);

        assertThat(writer.writeScorecard(WORKSPACE, PROJECT, CANDIDATE, CONSULTANT, DRAFT)).isSameAs(written);
        verify(scorecards, times(2)).applyAiDraft(WORKSPACE, PROJECT, CANDIDATE, CONSULTANT, DRAFT);
    }

    @Test
    @DisplayName("a second lost race is the caller's: it is never retried forever")
    void aSecondLostRaceIsThrown() {
        when(scorecards.applyAiDraft(any(), any(), any(), any(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(CandidateScorecard.class, CANDIDATE));

        assertThatThrownBy(() -> writer.writeScorecard(WORKSPACE, PROJECT, CANDIDATE, CONSULTANT, DRAFT))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        verify(scorecards, times(2)).applyAiDraft(WORKSPACE, PROJECT, CANDIDATE, CONSULTANT, DRAFT);
    }
}

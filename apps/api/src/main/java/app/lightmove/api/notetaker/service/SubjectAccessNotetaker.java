package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.constant.AiUse;
import app.lightmove.api.candidate.model.SubjectAccessContribution;
import app.lightmove.api.candidate.model.SubjectAccessSection;
import app.lightmove.api.candidate.model.SubjectAccessSubject;
import app.lightmove.api.candidate.service.SubjectAccessContributor;
import app.lightmove.api.core.export.service.RowSnapshotReader;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A subject access export's recorded calls (#919): each Notetaker recording with its transcript, the changes it
 * suggested and the live copilot's reading, and each interview plan. Recall's ids, the live URL's hash and the
 * workers' claims and counters are withheld; the video itself is Recall's, gone after its retention.
 */
@Component
@RequiredArgsConstructor
class SubjectAccessNotetaker implements SubjectAccessContributor {

    private final RowSnapshotReader rows;

    @Override
    public Set<String> tables() {
        return Set.of("app_lm_meeting_recording", "app_lm_interview_plan");
    }

    @Override
    public SubjectAccessContribution contributionOf(SubjectAccessSubject subject) {
        Map<String, Object> parameters = subject.parameters();
        List<Map<String, Object>> recordings = rows.rowsOf("""
                SELECT * FROM app_lm_meeting_recording WHERE workspace_id = :workspaceId AND person_id = :personId
                ORDER BY starts_at, id""", parameters,
                Set.of("workspace_id", "version", "recall_bot_id", "live_token_hash", "processing_since",
                        "suggesting_since", "coaching_since", "coached_through", "coach_runs", "coach_failures",
                        "live_charged", "transcript_id", "final_transcript_id", "reconciled_at"));
        List<Map<String, Object>> plans = rows.rowsOf("""
                SELECT * FROM app_lm_interview_plan WHERE workspace_id = :workspaceId AND person_id = :personId
                ORDER BY starts_at, id""", parameters,
                Set.of("workspace_id", "version", "title_key", "drafting_since"));
        Set<AiUse> uses = EnumSet.noneOf(AiUse.class);
        if (recordings.stream().anyMatch(recording -> recording.get("note_id") != null)) {
            uses.add(AiUse.MEETING_SUMMARY);
        }
        if (recordings.stream().anyMatch(recording -> recording.get("suggestions") != null)) {
            uses.add(AiUse.CALL_CHANGES);
        }
        if (recordings.stream().anyMatch(recording -> recording.get("live") != null)) {
            uses.add(AiUse.LIVE_COACH);
        }
        if (plans.stream().anyMatch(plan -> plan.get("drafted_at") != null)) {
            uses.add(AiUse.INTERVIEW_PLAN);
        }
        return new SubjectAccessContribution(List.of(
                new SubjectAccessSection("recordings", "Recorded calls", recordings),
                new SubjectAccessSection("interviewPlans", "Interview questions", plans)), uses);
    }
}

package app.lightmove.api.outreach.service;

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
 * A subject access export's outreach (#919): every sequence run, every email both ways with its words, the files an
 * email carried (named, not copied: each was one of the person's documents, which the export holds, or the
 * consultant's own) and every calendar meeting kept with the person. Provider ids and the dispatcher's claims are
 * withheld.
 */
@Component
@RequiredArgsConstructor
class SubjectAccessOutreach implements SubjectAccessContributor {

    private final RowSnapshotReader rows;

    @Override
    public Set<String> tables() {
        return Set.of("app_lm_outreach_enrollment", "app_lm_outreach_message", "app_lm_outreach_message_attachment",
                "app_lm_person_meeting");
    }

    @Override
    public SubjectAccessContribution contributionOf(SubjectAccessSubject subject) {
        Map<String, Object> parameters = subject.parameters();
        List<Map<String, Object>> runs = rows.rowsOf("""
                SELECT * FROM app_lm_outreach_enrollment WHERE workspace_id = :workspaceId AND person_id = :personId
                ORDER BY enrolled_at, id""", parameters,
                Set.of("workspace_id", "version", "sending_since", "thread_id", "last_message_id", "thread_gateway"));
        List<SubjectAccessSection> sections = List.of(
                new SubjectAccessSection("outreachRuns", "Email sequences", runs),
                new SubjectAccessSection("emails", "Emails", rows.rowsOf("""
                        SELECT * FROM app_lm_outreach_message
                        WHERE workspace_id = :workspaceId AND person_id = :personId
                        ORDER BY occurred_at, created_at, id""", parameters,
                        Set.of("workspace_id", "version", "provider_message_id", "thread_id", "in_reply_to",
                                "sending_since", "thread_gateway"))),
                new SubjectAccessSection("emailAttachments", "Files attached to emails", rows.rowsOf("""
                        SELECT a.* FROM app_lm_outreach_message_attachment a
                        JOIN app_lm_outreach_message m ON m.id = a.message_id
                        WHERE m.workspace_id = :workspaceId AND m.person_id = :personId
                        ORDER BY a.message_id, a.position""", parameters, Set.of("workspace_id", "storage_key"))),
                new SubjectAccessSection("meetings", "Meetings", rows.rowsOf("""
                        SELECT * FROM app_lm_person_meeting
                        WHERE workspace_id = :workspaceId AND person_id = :personId
                        ORDER BY starts_at, id""", parameters,
                        Set.of("workspace_id", "version", "mailbox_connection_id", "provider_event_id",
                                "zoom_meeting_id"))));
        Set<AiUse> uses = EnumSet.noneOf(AiUse.class);
        if (runs.stream().anyMatch(run -> run.get("opener") != null)) {
            uses.add(AiUse.OUTREACH_OPENER);
        }
        return new SubjectAccessContribution(sections, uses);
    }
}

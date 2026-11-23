package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.model.DraftedOpener;
import java.util.UUID;

/**
 * {@code opener} null: the model could not draft one this time, and the consultant writes it. {@code promptVersion}
 * is sent back with the enrolment, so the opener kept or rewritten is counted against the version that drafted it.
 */
public record DraftedOpenerResponse(UUID candidateId, String opener, Integer promptVersion) {

    public static DraftedOpenerResponse of(DraftedOpener drafted) {
        return new DraftedOpenerResponse(drafted.candidateId(), drafted.opener(), drafted.promptVersion());
    }
}

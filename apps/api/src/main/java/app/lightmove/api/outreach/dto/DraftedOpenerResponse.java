package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.model.DraftedOpener;
import java.util.UUID;

/** {@code opener} null: the model could not draft one this time, and the consultant writes it. */
public record DraftedOpenerResponse(UUID candidateId, String opener) {

    public static DraftedOpenerResponse of(DraftedOpener drafted) {
        return new DraftedOpenerResponse(drafted.candidateId(), drafted.opener());
    }
}

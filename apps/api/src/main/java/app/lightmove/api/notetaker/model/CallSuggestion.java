package app.lightmove.api.notetaker.model;

import app.lightmove.api.notetaker.constant.CallSuggestionKind;
import app.lightmove.api.notetaker.constant.CallSuggestionStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * One change a recorded call suggests for the person's record (V152), with the words it rests on and the time into
 * the call they were said. Kept on the recording, staff-only, and purged with its transcript.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CallSuggestion(String id, CallSuggestionKind kind, SuggestedValue value, String said, String at,
                             CallSuggestionStatus status, AppliedChange applied) {

    /**
     * A random id, never a slug of the kind: asking again adds suggestions beside the decided ones, and a tab still
     * open on the earlier answer must not apply a different suggestion that happened to share its id.
     */
    public static CallSuggestion pending(CallSuggestionKind kind, SuggestedValue value, String said, String at) {
        return new CallSuggestion(UUID.randomUUID().toString(), kind, value, said, at, CallSuggestionStatus.PENDING,
                null);
    }

    public boolean isPending() {
        return status == CallSuggestionStatus.PENDING;
    }

    public CallSuggestion appliedAs(SuggestedValue taken, AppliedChange change) {
        return new CallSuggestion(id, kind, taken, said, at, CallSuggestionStatus.APPLIED, change);
    }

    public CallSuggestion skipped() {
        return new CallSuggestion(id, kind, value, said, at, CallSuggestionStatus.SKIPPED, null);
    }

    /** Back to waiting with the value it was applied as, so the card shows what the researcher last chose. */
    public CallSuggestion reopened() {
        return new CallSuggestion(id, kind, value, said, at, CallSuggestionStatus.PENDING, null);
    }
}

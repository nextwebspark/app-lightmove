package app.lightmove.api.notetaker.model;

import app.lightmove.api.notetaker.constant.CallSuggestionKind;
import app.lightmove.api.notetaker.constant.CallSuggestionStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One change a recorded call suggests for the person's record (V152), with the words it rests on and the time into
 * the call they were said. Kept on the recording, staff-only, and purged with its transcript.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CallSuggestion(String id, CallSuggestionKind kind, SuggestedValue value, String said, String at,
                             CallSuggestionStatus status, AppliedChange applied) {

    public static CallSuggestion pending(String id, CallSuggestionKind kind, SuggestedValue value, String said,
                                         String at) {
        return new CallSuggestion(id, kind, value, said, at, CallSuggestionStatus.PENDING, null);
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

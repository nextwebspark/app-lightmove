package app.lightmove.api.notetaker.dto;

import app.lightmove.api.notetaker.constant.CallSuggestionKind;
import app.lightmove.api.notetaker.constant.CallSuggestionStatus;
import app.lightmove.api.notetaker.model.SuggestedValue;
import java.time.Instant;

/**
 * One change a call suggests, shown against what the record holds now ({@code current}, read live). {@code available}
 * is false where the record moved on so it can no longer be applied — the tag was retired, say — and {@code edited}
 * where an applied one was changed by the researcher before it went in. An applied one also says what it replaced
 * ({@code was}, where it replaced something and that was kept), when and by whom.
 */
public record CallSuggestionResponse(String id, CallSuggestionKind kind, CallSuggestionStatus status,
                                     SuggestedValue value, SuggestedValue current, String said, String at,
                                     boolean available, boolean edited, SuggestedValue was, Instant appliedAt,
                                     String appliedBy) {}

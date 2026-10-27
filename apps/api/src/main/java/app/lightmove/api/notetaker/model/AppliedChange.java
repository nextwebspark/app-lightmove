package app.lightmove.api.notetaker.model;

import app.lightmove.api.candidate.constant.CallField;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Who applied a suggestion, when, whether they edited it first, which of the record's fields it wrote, and what the
 * record held before ({@code was}: null where the change adds rather than replaces, and on a change applied before it
 * was kept).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppliedChange(UUID by, Instant at, boolean edited, List<CallField> fields, SuggestedValue was) {}

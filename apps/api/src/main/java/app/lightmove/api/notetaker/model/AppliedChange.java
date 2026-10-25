package app.lightmove.api.notetaker.model;

import app.lightmove.api.candidate.constant.CallField;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Who applied a suggestion, when, whether they edited it first, and which of the record's fields it wrote. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppliedChange(UUID by, Instant at, boolean edited, List<CallField> fields) {}

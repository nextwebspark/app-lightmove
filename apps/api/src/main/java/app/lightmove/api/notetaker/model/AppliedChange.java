package app.lightmove.api.notetaker.model;

import app.lightmove.api.candidate.constant.CallField;
import app.lightmove.api.candidate.model.CandidateScorecard.SectionStanding;
import app.lightmove.api.candidate.model.ProfileFromCall;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What applying a suggestion wrote and what it replaced, so Undo can put it back — and refuse to when the record has
 * moved on since. Only the part its kind writes is set.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AppliedChange(UUID by, Instant at, boolean edited, List<CallField> fields,
                            ProfileFromCall profileBefore, ProfileFromCall profileAfter,
                            String sectionBefore, String sectionAfter, SectionStanding sectionStanding,
                            UUID tagId, boolean tagAdded, UUID taskId) {}

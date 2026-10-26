package app.lightmove.api.notetaker.dto;

import app.lightmove.api.notetaker.constant.RecordGap;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The interview plan for one call. {@code drafted} is false until someone pressed Prepare questions; {@code missing}
 * is what the record lacks today, which the gap questions were written for.
 */
public record InterviewPlanResponse(UUID meetingId, String title, Instant startsAt, boolean drafted,
                                    Instant savedAt, String savedByName,
                                    List<PlanCompetencyResponse> competencies, List<RecordGap> missing,
                                    List<InterviewQuestionDto> questions) {}

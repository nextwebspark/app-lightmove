package app.lightmove.api.notetaker.dto;

import app.lightmove.api.notetaker.constant.CoachState;
import app.lightmove.api.notetaker.constant.LiveCopilotStatus;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The copilot of one live call as its window draws it. {@code lines} are the finished lines after the caller's cursor
 * and {@code lastSeq} the cursor to send next; {@code speaking} is each voice's words so far. The call clock counts
 * from {@code recordingStartedAt} once Recall reports recording, from {@code startsAt} before. Staff-only.
 */
public record CopilotResponse(UUID recordingId, String title, String candidateName, Instant startsAt,
                              Instant recordingStartedAt, NotetakerStatus status, LiveCopilotStatus liveStatus,
                              CoachState coach,
                              String stoppedByName, boolean mayStop,
                              List<CopilotCoverageResponse> coverage, List<CopilotGapResponse> gaps,
                              List<CopilotQuestionResponse> plan, List<CopilotNudgeResponse> nudges,
                              List<LiveLineResponse> lines, List<LiveLineResponse> speaking, long lastSeq,
                              List<CopilotViewerResponse> viewers) {}

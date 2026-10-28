package app.lightmove.api.notetaker.dto;

import java.util.List;
import java.util.UUID;

/**
 * Whether the Notetaker is offered here, every bot this position sent into a call with the person (newest call first),
 * the upcoming meeting rows the caller may send one to — calls on their own calendar or booked by them, with a
 * link — and which of those fall on a day that has begun in the caller's time zone, where Send now asks nothing first.
 */
public record CandidateNotetakerResponse(boolean offered, List<NotetakerRecordingResponse> recordings,
                                         List<UUID> recordableMeetingIds, List<UUID> callDayMeetingIds) {}

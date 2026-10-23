package app.lightmove.api.notetaker.dto;

import java.util.List;
import java.util.UUID;

/**
 * Whether the Notetaker is offered here, every bot this position sent into a call with the person (newest call first),
 * and the upcoming meeting rows the caller may send one to: calls on their own calendar or booked by them, with a link.
 */
public record CandidateNotetakerResponse(boolean offered, List<NotetakerRecordingResponse> recordings,
                                         List<UUID> recordableMeetingIds) {}

package app.lightmove.api.notetaker.dto;

import java.util.List;

/** Whether the Notetaker is offered here, and every bot sent into a call with this person, newest call first. */
public record CandidateNotetakerResponse(boolean offered, List<NotetakerRecordingResponse> recordings) {}

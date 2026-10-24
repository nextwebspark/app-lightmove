package app.lightmove.api.notetaker.dto;

import app.lightmove.api.notetaker.constant.RecordGap;

/** A fact the record is missing, ticked once the executive has spoken to it. */
public record CopilotGapResponse(RecordGap gap, boolean heard, String at) {}

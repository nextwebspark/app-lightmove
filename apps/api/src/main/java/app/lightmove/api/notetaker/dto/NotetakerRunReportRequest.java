package app.lightmove.api.notetaker.dto;

import app.lightmove.api.notetaker.constant.NotetakerBotState;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import jakarta.validation.constraints.NotNull;

/** A bot's report of where it has got to; a failure names its reason. */
public record NotetakerRunReportRequest(@NotNull NotetakerBotState state, NotetakerEndReason reason) {}

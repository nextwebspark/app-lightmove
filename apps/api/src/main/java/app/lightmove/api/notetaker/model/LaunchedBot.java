package app.lightmove.api.notetaker.model;

import app.lightmove.api.notetaker.constant.MeetingRecorderKind;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;

/** Whose bot went, its id at that recorder, and — for a backup sent because ours failed — why. */
public record LaunchedBot(MeetingRecorderKind recorder, String externalId, NotetakerEndReason fallbackReason) {}

package app.lightmove.api.notetaker.model;

import app.lightmove.api.notetaker.constant.RecordGap;

/** One of the record's gaps the executive spoke to during a live call, and when. What they said is not kept here. */
public record GapHeard(RecordGap gap, String at) {}

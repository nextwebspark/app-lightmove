package app.lightmove.api.notetaker.model;

import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import app.lightmove.api.outreach.model.KeptMeeting;

/** The call the scheduler is claiming, read off the calendar row of the consultant whose bot goes. */
public record ClaimedCall(String callKey, NotetakerPlatform platform, KeptMeeting sentFrom) {}

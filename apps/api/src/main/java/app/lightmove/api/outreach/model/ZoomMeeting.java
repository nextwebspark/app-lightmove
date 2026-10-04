package app.lightmove.api.outreach.model;

/** A meeting made on a consultant's Zoom account: its id, to delete it again, and the link an invite carries. */
public record ZoomMeeting(String id, String joinUrl) {}

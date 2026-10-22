package app.lightmove.api.billing.overview.dto;

/** The notetaker's recorded minutes this billing month, and the fair-use ceiling; null where none is set. */
public record NotetakerMinutesResponse(long used, Long ceiling) {}

package app.lightmove.api.core.storage.model;

/** The bytes a public address answered with, before anything has decoded them. */
public record RemoteImage(byte[] content, String contentType) {}

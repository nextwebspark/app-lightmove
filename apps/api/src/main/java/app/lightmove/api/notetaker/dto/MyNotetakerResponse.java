package app.lightmove.api.notetaker.dto;

/** Settings → Profile's "Notetaker joins my meetings", beside the workspace's switch it sits under. */
public record MyNotetakerResponse(boolean offered, boolean workspaceEnabled, boolean enabled) {}

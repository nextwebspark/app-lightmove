package app.lightmove.api.notetaker.dto;

import jakarta.validation.constraints.NotNull;

/** One Notetaker switch, turned on or off. */
public record ChangeNotetakerRequest(@NotNull(message = "Say whether the Notetaker is on") Boolean enabled) {}

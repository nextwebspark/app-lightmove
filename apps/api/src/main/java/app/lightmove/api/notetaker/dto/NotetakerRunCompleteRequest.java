package app.lightmove.api.notetaker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** A bot leaving: the storage keys of the audio it captured, in order, and of its captions. */
public record NotetakerRunCompleteRequest(@Size(max = 1000) List<@NotBlank String> audioKeys, String captionsKey) {}

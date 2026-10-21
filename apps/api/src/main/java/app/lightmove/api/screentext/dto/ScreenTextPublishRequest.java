package app.lightmove.api.screentext.dto;

import jakarta.validation.constraints.Size;

public record ScreenTextPublishRequest(@Size(max = 500) String note) {
}

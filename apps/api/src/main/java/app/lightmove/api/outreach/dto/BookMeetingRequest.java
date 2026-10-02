package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.MeetingVideo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** A call to book: one of the offered starts, its length, the video link, which of their addresses, and the title they see. */
public record BookMeetingRequest(
        @NotNull Instant startsAt,
        @NotNull Integer minutes,
        @NotNull MeetingVideo video,
        @NotBlank @Size(max = 320) String inviteAddress,
        @NotBlank @Size(max = 200) String title) {}

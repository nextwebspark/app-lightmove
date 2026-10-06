package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.constant.SequenceStartMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * The reviewed people and when their first emails go. A null mode waits for the next sending window;
 * {@code startAt} is read only with {@code AT}.
 */
public record StartSequenceRequest(
        @NotEmpty(message = "Choose someone to add")
        @Size(max = 50, message = "Add at most fifty people at a time")
        List<@Valid @NotNull EnrollPersonRequest> people,
        SequenceStartMode startMode,
        Instant startAt) {

    public SequenceStartMode startModeOrDefault() {
        return startMode == null ? SequenceStartMode.NEXT_WINDOW : startMode;
    }
}

package app.lightmove.api.outreach.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record StartSequenceRequest(
        @NotEmpty(message = "Choose someone to add")
        @Size(max = 50, message = "Add at most fifty people at a time")
        List<@Valid @NotNull EnrollPersonRequest> people) {}

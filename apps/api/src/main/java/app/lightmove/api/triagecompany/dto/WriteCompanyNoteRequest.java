package app.lightmove.api.triagecompany.dto;

import app.lightmove.api.triagecompany.model.CompanyNote;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A note on a company, to write or revise. {@code format} is a {@code NoteFormat} token, plain when absent. */
public record WriteCompanyNoteRequest(
        @NotBlank String kind,
        @NotBlank @Size(max = CompanyNote.MAX_BODY) String body,
        String format
) {}

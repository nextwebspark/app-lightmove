package app.lightmove.api.candidate.dto;

/** What an upload became, and the document it now belongs to. */
public record PersonDocumentUploadResponse(
        /** A {@code PersonDocumentUploadOutcome} wire token: {@code created} or {@code new_version}. */
        String outcome,
        PersonDocumentResponse document
) {}

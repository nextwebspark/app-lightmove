package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.model.PersonDocument;
import jakarta.validation.constraints.Size;

/** A change to a document's card; each field left null is left as it is. */
public record UpdatePersonDocumentRequest(
        @Size(max = PersonDocument.MAX_TITLE) String title,
        /** A {@code PersonDocumentCategory} wire token. */
        String category,
        /** True makes it the person's CV, taking the mark from any other; false only clears it here. */
        Boolean primaryCv
) {}

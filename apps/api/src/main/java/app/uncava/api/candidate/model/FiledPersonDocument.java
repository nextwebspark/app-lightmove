package app.uncava.api.candidate.model;

import app.uncava.api.candidate.constant.PersonDocumentUploadOutcome;

/** An upload once its rows are written: the document it founded or joined. */
public record FiledPersonDocument(PersonDocumentUploadOutcome outcome, PersonDocument document) {}

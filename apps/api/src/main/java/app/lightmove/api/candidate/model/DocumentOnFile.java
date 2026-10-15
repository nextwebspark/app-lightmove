package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.PersonDocumentCategory;
import java.time.Instant;

/** The latest file of one of a person's documents, as the AI reads it. */
public record DocumentOnFile(String title, PersonDocumentCategory category, boolean primaryCv, Instant uploadedAt,
                             StoredDocumentFile file) {}

package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.PersonDocumentCategory;

/** The latest file of one of a person's documents, as the AI reads it. */
public record DocumentOnFile(String title, PersonDocumentCategory category, StoredDocumentFile file) {}

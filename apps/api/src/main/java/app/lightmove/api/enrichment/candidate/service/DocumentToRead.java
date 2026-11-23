package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.PersonDocumentCategory;
import java.util.function.IntFunction;

/**
 * A document a prompt may quote from: its title, what it is, and how to read its text up to a number of characters.
 * {@code textUpTo} answers null for one no text can be read from, which is then left out.
 */
record DocumentToRead(String title, PersonDocumentCategory category, IntFunction<String> textUpTo) {}

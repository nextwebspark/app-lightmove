package app.lightmove.api.enrichment.importrun.model;

import app.lightmove.api.enrichment.importrun.constant.ImportEnrichmentKind;
import java.util.UUID;

/** One person or company an import filed with a LinkedIn link: a candidate's or a triage company's id, and its slug. */
public record ImportEnrichmentTarget(ImportEnrichmentKind kind, UUID targetId, String linkedinSlug) {}
